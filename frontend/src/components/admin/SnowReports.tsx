import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { snowReportApi, type SnowReportInput } from '../../api/admin.ts'
import type { SnowReport } from '../../api/catalog.ts'
import { fieldErrors } from '../../auth/errorMessages.ts'
import { useAuth } from '../../auth/useAuth.ts'
import { dayLabel, feet, todayIst, toFeet, toMetres } from '../../lib/format.ts'
import { shrinkPhoto } from '../../lib/photos.ts'
import { TextField } from '../auth/TextField.tsx'
import { TextAreaField } from '../profile/fields.tsx'
import { Button, ErrorNote, Loading, Panel } from './AdminUi.tsx'

type Form = {
  reported_on: string
  reported_from: string
  snowline_ft: string
  night_temp_c: string
  conditions: { label: string; value: string }[]
  crowd_place: string
  crowd_tents: string
  note: string
}

/** A new form starts from last week's labels and places, so only this week's readings need typing. */
function blankForm(last: SnowReport | undefined, place: string): Form {
  return {
    reported_on: todayIst(),
    reported_from: last?.reported_from ?? place,
    snowline_ft: '',
    night_temp_c: '',
    conditions: last?.conditions.map((c) => ({ label: c.label, value: '' })) ?? [
      { label: '', value: '' },
      { label: '', value: '' },
    ],
    crowd_place: last?.crowd_place ?? '',
    crowd_tents: '',
    note: '',
  }
}

const numberOrNull = (v: string) => (v.trim() === '' ? null : Number(v))

/**
 * Weekly snow reports for one trek (docs/TRD.md §7.13): file this week's, see every past one. Used by admins
 * (any trek) and guides (treks they lead). Reports can't be edited; a correction is a newer report.
 */
export function SnowReports({ area, trackId, place }: { area: 'admin' | 'guide'; trackId: string; place: string }) {
  const { withAuth } = useAuth()
  const api = snowReportApi(area)
  const queryKey = ['snow-reports', area, trackId]
  const reports = useQuery({ queryKey, queryFn: () => withAuth((token) => api.list(token, trackId)) })

  if (reports.isPending) return <Loading />
  if (reports.isError) return <ErrorNote error={reports.error} />
  const items = reports.data.items
  return (
    <>
      <NewReport key={items[0]?.id ?? 'first'} area={area} trackId={trackId} last={items[0]} place={place} queryKey={queryKey} />
      <Panel title={`Past reports (${items.length})`}>
        {items.length === 0 ? (
          <p className="text-sm text-stone-600">None yet. The newest report shows on the trek page.</p>
        ) : (
          <ul className="divide-y divide-stone-100">
            {items.map((r, i) => (
              <li key={r.id} className="flex gap-3 py-3">
                {r.photo_url ? (
                  <img src={r.photo_url} alt="" loading="lazy" className="size-16 shrink-0 rounded-lg object-cover" />
                ) : (
                  <span className="size-16 shrink-0 rounded-lg bg-stone-100" />
                )}
                <div className="min-w-0 text-sm">
                  <p className="font-medium text-stone-900">
                    {dayLabel(r.reported_on)} · from {r.reported_from}
                    {i === 0 && <span className="ml-2 rounded-full bg-brand-100 px-2 py-0.5 text-xs text-brand-800">On the trek page</span>}
                  </p>
                  <p className="text-stone-600">
                    {[
                      r.snowline_m !== null && `snowline ${feet(r.snowline_m)}`,
                      r.night_temp_c !== null && `${r.night_temp_c} °C at night`,
                      ...r.conditions.map((c) => `${c.label}: ${c.value}`),
                      r.crowd_tents !== null && `${r.crowd_tents} tents at ${r.crowd_place}`,
                    ]
                      .filter(Boolean)
                      .join(' · ') || 'No readings'}
                  </p>
                  {r.note && <p className="mt-1 text-stone-500">{r.note}</p>}
                  <p className="mt-1 text-xs text-stone-400">By {r.reported_by.full_name ?? 'someone'}</p>
                </div>
              </li>
            ))}
          </ul>
        )}
      </Panel>
    </>
  )
}

function NewReport({
  area,
  trackId,
  last,
  place,
  queryKey,
}: {
  area: 'admin' | 'guide'
  trackId: string
  last: SnowReport | undefined
  place: string
  queryKey: string[]
}) {
  const { withAuth } = useAuth()
  const queryClient = useQueryClient()
  const api = snowReportApi(area)
  const [form, setForm] = useState<Form>(() => blankForm(last, place))
  const [photo, setPhoto] = useState<File | null>(null)
  const set = <K extends keyof Form>(key: K, value: Form[K]) => setForm((f) => ({ ...f, [key]: value }))

  const create = useMutation({
    mutationFn: async () => {
      const body: SnowReportInput = {
        reported_on: form.reported_on,
        reported_from: form.reported_from,
        snowline_m: form.snowline_ft.trim() === '' ? null : toMetres(Number(form.snowline_ft)),
        night_temp_c: numberOrNull(form.night_temp_c),
        conditions: form.conditions.filter((c) => c.label.trim() || c.value.trim()),
        crowd_place: form.crowd_place.trim() || null,
        crowd_tents: numberOrNull(form.crowd_tents),
        note: form.note.trim() || null,
      }
      const report = await withAuth((token) => api.create(token, trackId, body))
      if (photo) {
        const blob = await shrinkPhoto(photo).catch(() => photo)
        await withAuth((token) => api.addPhoto(token, report.id, blob))
      }
    },
    // Remounts this form (keyed by the newest report) with next week's defaults.
    onSettled: () => void queryClient.invalidateQueries({ queryKey }),
  })
  const errors = fieldErrors(create.error)
  const setCondition = (i: number, patch: Partial<Form['conditions'][number]>) =>
    set('conditions', form.conditions.map((c, j) => (j === i ? { ...c, ...patch } : c)))

  const submit = (e: FormEvent) => {
    e.preventDefault()
    create.mutate()
  }

  return (
    <Panel title="This week's report">
      <p className="text-sm text-stone-600">
        File it every Tuesday. The newest one shows on the trek page; every report is kept.
      </p>
      <form onSubmit={submit} className="mt-4 grid gap-4 sm:grid-cols-2">
        <TextField label="Observed on" name="reported_on" type="date" required max={todayIst()} value={form.reported_on}
          onChange={(e) => set('reported_on', e.target.value)} error={errors.reported_on} />
        <TextField label="Reported from" name="reported_from" required maxLength={60} value={form.reported_from}
          onChange={(e) => set('reported_from', e.target.value)} error={errors.reported_from} hint="Shown as “Snow report from …”" />
        <TextField label="Snowline altitude" name="snowline_ft" type="number" min={100} max={29000} suffix="ft"
          value={form.snowline_ft} onChange={(e) => set('snowline_ft', e.target.value)} error={errors.snowline_m}
          hint={last?.snowline_m ? `Last week: ${toFeet(last.snowline_m).toLocaleString('en-IN')} ft` : undefined} />
        <TextField label="Night temperature, base camp" name="night_temp_c" type="number" min={-60} max={50} suffix="°C"
          value={form.night_temp_c} onChange={(e) => set('night_temp_c', e.target.value)} error={errors.night_temp_c} />

        <fieldset className="space-y-3 sm:col-span-2">
          <legend className="text-sm font-medium text-stone-800">Other readings</legend>
          <p className="text-xs text-stone-500">E.g. “Juda ka Talab”: “Frozen”, “Road, Purola to Sankri”: “Open”. Up to four.</p>
          {form.conditions.map((c, i) => (
            <div key={i} className="grid grid-cols-[1fr_1fr_auto] items-end gap-2">
              <TextField label="What" name={`condition-${i}-label`} maxLength={40} value={c.label}
                onChange={(e) => setCondition(i, { label: e.target.value })} error={errors[`conditions[${i}].label`]} />
              <TextField label="Now" name={`condition-${i}-value`} maxLength={60} value={c.value}
                onChange={(e) => setCondition(i, { value: e.target.value })} error={errors[`conditions[${i}].value`]} />
              <button type="button" onClick={() => set('conditions', form.conditions.filter((_, j) => j !== i))}
                className="mb-2.5 text-sm text-laterite-600 hover:text-laterite-500">
                Remove
              </button>
            </div>
          ))}
          {form.conditions.length < 4 && (
            <Button tone="secondary" onClick={() => set('conditions', [...form.conditions, { label: '', value: '' }])}>
              Add a reading
            </Button>
          )}
        </fieldset>

        <TextField label="Tents counted at" name="crowd_place" maxLength={60} value={form.crowd_place}
          onChange={(e) => set('crowd_place', e.target.value)} error={errors.crowd_place} hint="The busiest camp, e.g. Juda ka Talab" />
        <TextField label="Number of tents" name="crowd_tents" type="number" min={0} max={2000} value={form.crowd_tents}
          onChange={(e) => set('crowd_tents', e.target.value)} error={errors.crowd_tents} hint="Published so trekkers can pick a quiet week." />
        <div className="sm:col-span-2">
          <TextAreaField label="Note (optional)" name="note" maxLength={500} rows={2} value={form.note}
            onChange={(e) => set('note', e.target.value)} error={errors.note} />
        </div>
        <label className="block text-sm sm:col-span-2">
          <span className="font-medium text-stone-800">Photo taken that morning (optional)</span>
          <input type="file" accept="image/jpeg,image/png" onChange={(e) => setPhoto(e.target.files?.[0] ?? null)}
            className="mt-1.5 block w-full text-sm text-stone-600 file:mr-3 file:rounded-full file:border-0 file:bg-stone-100 file:px-4 file:py-1.5 file:text-sm file:font-medium" />
        </label>
        {create.error && Object.keys(errors).length === 0 && (
          <div className="sm:col-span-2">
            <ErrorNote error={create.error} />
          </div>
        )}
        <div className="sm:col-span-2">
          <Button type="submit" disabled={create.isPending}>
            {create.isPending ? 'Filing…' : 'File report'}
          </Button>
        </div>
      </form>
    </Panel>
  )
}

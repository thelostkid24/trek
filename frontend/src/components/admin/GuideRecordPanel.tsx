import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import {
  getGuideRecord,
  listTracks,
  updateGuideRecord,
  type GuideRecord,
  type StatPick,
} from '../../api/admin.ts'
import type { GuideStatKey } from '../../api/catalog.ts'
import { PLEDGES, type PledgeKind } from '../../api/certifications.ts'
import type { GuideRouteKind, RouteItem } from '../../api/guide.ts'
import { fieldErrors } from '../../auth/errorMessages.ts'
import { useAuth } from '../../auth/useAuth.ts'
import { MONTHS, planOf } from '../../lib/guideRecord.ts'
import { TextField } from '../auth/TextField.tsx'
import { RoutePlanFields } from '../guide/RoutePlanFields.tsx'
import { SelectField } from '../profile/fields.tsx'
import { Button, ErrorNote, Loading } from './AdminUi.tsx'

const STATS: { key: GuideStatKey; label: string }[] = [
  { key: 'RATING', label: 'Rating (or “New · no reviews yet”)' },
  { key: 'TREKS_LED', label: 'Treks led (with us and before)' },
  { key: 'TREKS_LAST_3Y', label: 'Treks led in the last 3 years' },
  { key: 'YEARS_LEADING', label: 'Years as main guide' },
  { key: 'ROUTES_LED', label: 'Routes led' },
  { key: 'PEAK_CLIMBS', label: 'Peak climbs led' },
  { key: 'EVACUATIONS', label: '0 evacuations (needs the declaration)' },
  { key: 'COURSES', label: 'Mountaineering courses' },
  { key: 'TOP_COURSE', label: 'Highest course (AMC / BMC)' },
  { key: 'ROUTE_TIMES', label: 'Times one route was led' },
  { key: 'HIGHEST_PASS', label: 'Highest pass led' },
  { key: 'RETURN_RATE', label: 'Trekkers who come back' },
  { key: 'CUSTOM', label: 'Custom (type the key and value)' },
]

const MAX_STATS = 7

type Row = Omit<RouteItem, 'track_name'>

const emptyRoute = (): Row => ({
  id: null,
  track_id: null,
  name: null,
  kind: 'TREK',
  times_led: null,
  last_led_year: null,
  last_led_month: null,
  most_recent: false,
  availability: 'ASK',
  season_from_month: null,
  season_to_month: null,
  available_from: null,
  note: null,
})

/**
 * A guide's record from onboarding (docs/TRD.md §7.21.8): their routes with times and when they last led them, the
 * two safety pledges (audit-logged) and which numbers the page's stats row shows. Values are worked out on read.
 */
export function GuideRecordPanel({ guideId, onDone }: { guideId: string; onDone: () => void }) {
  const { withAuth } = useAuth()
  const record = useQuery({
    queryKey: ['admin-guide-record', guideId],
    queryFn: () => withAuth((token) => getGuideRecord(token, guideId)),
  })
  if (record.isPending) return <Loading />
  if (record.isError) return <ErrorNote error={record.error} />
  return <RecordForm guideId={guideId} initial={record.data} onDone={onDone} />
}

function RecordForm({ guideId, initial, onDone }: { guideId: string; initial: GuideRecord; onDone: () => void }) {
  const { withAuth } = useAuth()
  const queryClient = useQueryClient()
  const tracks = useQuery({ queryKey: ['admin-tracks'], queryFn: () => withAuth(listTracks) })
  const [treks3y, setTreks3y] = useState(initial.treks_last_3y === null ? '' : String(initial.treks_last_3y))
  const [routes, setRoutes] = useState<Row[]>(initial.routes.map(({ track_name: _t, ...r }) => r))
  const [pledges, setPledges] = useState<PledgeKind[]>(initial.pledges)
  const [stats, setStats] = useState<StatPick[]>(initial.stats)
  const save = useMutation({
    mutationFn: () =>
      withAuth((token) =>
        updateGuideRecord(token, guideId, { treks_last_3y: treks3y ? Number(treks3y) : null, routes, pledges, stats }),
      ),
    onSuccess: (data) => {
      queryClient.setQueryData(['admin-guide-record', guideId], data)
      void queryClient.invalidateQueries({ queryKey: ['admin-guide-details', guideId] })
      onDone()
    },
  })
  const errors = fieldErrors(save.error)
  const setRoute = (i: number, patch: Partial<Row>) => setRoutes(routes.map((r, j) => (j === i ? { ...r, ...patch } : r)))
  const removeRoute = (i: number) => {
    setRoutes(routes.filter((_, j) => j !== i))
    // Stats for that route go with it; later routes move up one.
    setStats(
      stats
        .filter((s) => s.route_index !== i)
        .map((s) => (s.route_index !== null && s.route_index > i ? { ...s, route_index: s.route_index - 1 } : s)),
    )
  }
  const moveUp = (i: number) => {
    setRoutes(move(routes, i, i - 1))
    // Stats follow their route.
    setStats(stats.map((s) => (s.route_index === i ? { ...s, route_index: i - 1 } : s.route_index === i - 1 ? { ...s, route_index: i } : s)))
  }
  const trackOptions = (tracks.data?.items ?? []).map((t) => ({ value: t.id, label: t.name }))
  const routeName = (r: Row) => (r.track_id ? (trackOptions.find((o) => o.value === r.track_id)?.label ?? 'Trek') : r.name || 'Unnamed route')
  const year = new Date().getFullYear()

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault()
        save.mutate()
      }}
      className="mt-3 space-y-5 rounded-xl border border-stone-200 p-4"
    >
      <TextField
        label="Treks led elsewhere in the last 3 years"
        name="treks_last_3y"
        type="number"
        min={0}
        max={1000}
        value={treks3y}
        onChange={(e) => setTreks3y(e.target.value)}
        error={errors.treks_last_3y}
        hint="From onboarding. Departures completed with us in that time are added on the page."
      />

      <fieldset>
        <legend className="text-sm font-medium text-stone-800">Routes, in the page's order</legend>
        <p className="mt-0.5 text-sm text-stone-500">
          Pick a trek we list, or type a route we don't. Times led are from before Sahyātri; departures completed with us are added.
        </p>
        {tracks.isError && <ErrorNote error={tracks.error} />}
        {errors.routes && <p className="mt-1 text-sm text-laterite-600">{errors.routes}</p>}
        <ol className="mt-3 space-y-4">
          {routes.map((r, i) => {
            const at = `routes[${i}]`
            return (
              <li key={r.id ?? `new-${i}`} className="space-y-3 rounded-lg bg-stone-50 p-3">
                <div className="grid gap-3 sm:grid-cols-2">
                  <SelectField
                    label="Trek we list"
                    name={`record-track-${i}`}
                    placeholder="— Not listed: type its name —"
                    options={trackOptions}
                    value={r.track_id ?? ''}
                    onChange={(e) => setRoute(i, { track_id: e.target.value || null, name: e.target.value ? null : r.name })}
                    error={errors[`${at}.track_id`]}
                  />
                  <TextField
                    label="Or its name"
                    name={`record-name-${i}`}
                    maxLength={80}
                    disabled={r.track_id !== null}
                    value={r.name ?? ''}
                    onChange={(e) => setRoute(i, { name: e.target.value || null })}
                    error={errors[`${at}.name`]}
                    placeholder="E.g. Friendship Peak"
                  />
                  <SelectField
                    label="Kind"
                    name={`record-kind-${i}`}
                    placeholder="Trek"
                    options={[{ value: 'PEAK', label: 'Peak climb' }]}
                    value={r.kind === 'PEAK' ? 'PEAK' : ''}
                    onChange={(e) => setRoute(i, { kind: (e.target.value || 'TREK') as GuideRouteKind })}
                  />
                  <TextField
                    label="Times led before (blank: “Led”)"
                    name={`record-times-${i}`}
                    type="number"
                    min={1}
                    max={1000}
                    value={r.times_led ?? ''}
                    onChange={(e) => setRoute(i, { times_led: e.target.value ? Number(e.target.value) : null })}
                    error={errors[`${at}.times_led`]}
                  />
                  <TextField
                    label="Last led: year"
                    name={`record-year-${i}`}
                    type="number"
                    min={1950}
                    max={year}
                    value={r.last_led_year ?? ''}
                    onChange={(e) =>
                      setRoute(i, e.target.value ? { last_led_year: Number(e.target.value) } : { last_led_year: null, last_led_month: null })
                    }
                    error={errors[`${at}.last_led_year`]}
                  />
                  <SelectField
                    label="Last led: month"
                    name={`record-month-${i}`}
                    placeholder="—"
                    disabled={r.last_led_year === null}
                    options={MONTHS.map((label, j) => ({ value: String(j + 1), label }))}
                    value={r.last_led_month === null ? '' : String(r.last_led_month)}
                    onChange={(e) => setRoute(i, { last_led_month: e.target.value ? Number(e.target.value) : null })}
                  />
                </div>
                <label className="flex items-center gap-2 text-sm text-stone-700">
                  <input
                    type="checkbox"
                    checked={r.most_recent}
                    onChange={(e) => setRoute(i, { most_recent: e.target.checked })}
                    className="size-4 rounded border-stone-300"
                  />
                  Their most recent trek or climb
                </label>
                <RoutePlanFields plan={r} onChange={(plan) => setRoute(i, planOf(plan))} name={`record-plan-${i}`} errors={errors} prefix={at} />
                <div className="flex gap-4 text-sm">
                  <button type="button" disabled={i === 0} onClick={() => moveUp(i)} className="text-stone-500 hover:text-stone-800 disabled:opacity-40">
                    Move up
                  </button>
                  <button type="button" onClick={() => removeRoute(i)} className="text-stone-500 hover:text-stone-800">
                    Remove
                  </button>
                </div>
              </li>
            )
          })}
        </ol>
        <button type="button" onClick={() => setRoutes([...routes, emptyRoute()])} className="mt-3 text-sm font-semibold text-brand-800 hover:text-brand-900">
          + Add a route
        </button>
      </fieldset>

      <fieldset>
        <legend className="text-sm font-medium text-stone-800">Safety pledges from onboarding</legend>
        <div className="mt-2 space-y-2">
          {(Object.keys(PLEDGES) as PledgeKind[]).map((k) => (
            <label key={k} className="flex items-center gap-2 text-sm text-stone-700">
              <input
                type="checkbox"
                checked={pledges.includes(k)}
                onChange={(e) => setPledges(e.target.checked ? [...pledges, k] : pledges.filter((p) => p !== k))}
                className="size-4 rounded border-stone-300"
              />
              {PLEDGES[k].title} <span className="text-stone-500">({PLEDGES[k].status})</span>
            </label>
          ))}
        </div>
      </fieldset>

      <fieldset>
        <legend className="text-sm font-medium text-stone-800">Stats row, in order (up to {MAX_STATS})</legend>
        <p className="mt-0.5 text-sm text-stone-500">None picked: the page shows its default row. Values are worked out from the record, reviews and treks, except Custom, which shows what you type and doesn't update itself.</p>
        <ol className="mt-2 space-y-2">
          {stats.map((s, i) => (
            <li key={i} className="grid grid-cols-[1fr_auto] items-end gap-2 sm:grid-cols-[1fr_1fr_auto]">
              <SelectField
                label={`Stat ${i + 1}`}
                name={`record-stat-${i}`}
                options={STATS.map((o) => ({ value: o.key, label: o.label }))}
                value={s.key}
                onChange={(e) => {
                  const key = e.target.value as GuideStatKey
                  const custom = key === 'CUSTOM'
                  setStats(stats.map((x, j) => (j === i
                    ? { key, route_index: key === 'ROUTE_TIMES' ? (x.route_index ?? 0) : null, value: custom ? (x.value ?? '') : null, label: custom ? (x.label ?? '') : null }
                    : x)))
                }}
                error={errors[`stats[${i}].key`]}
              />
              {s.key === 'ROUTE_TIMES' && (
                <SelectField
                  label="Route"
                  name={`record-stat-route-${i}`}
                  options={routes.map((r, j) => ({ value: String(j), label: routeName(r) }))}
                  value={s.route_index === null ? '' : String(s.route_index)}
                  onChange={(e) => setStats(stats.map((x, j) => (j === i ? { ...x, route_index: e.target.value ? Number(e.target.value) : null } : x)))}
                  error={errors[`stats[${i}].route_index`]}
                />
              )}
              {s.key === 'CUSTOM' && (
                <div className="grid grid-cols-[2fr_1fr] items-end gap-2">
                  <TextField
                    label="Key"
                    name={`record-stat-label-${i}`}
                    required
                    maxLength={80}
                    placeholder="treks led in 3 years"
                    value={s.label ?? ''}
                    onChange={(e) => setStats(stats.map((x, j) => (j === i ? { ...x, label: e.target.value } : x)))}
                    error={errors[`stats[${i}].label`]}
                  />
                  <TextField
                    label="Value"
                    name={`record-stat-value-${i}`}
                    required
                    maxLength={20}
                    placeholder="21"
                    value={s.value ?? ''}
                    onChange={(e) => setStats(stats.map((x, j) => (j === i ? { ...x, value: e.target.value } : x)))}
                    error={errors[`stats[${i}].value`]}
                  />
                </div>
              )}
              <button type="button" onClick={() => setStats(stats.filter((_, j) => j !== i))} className="mb-2.5 text-sm text-stone-500 hover:text-stone-800 sm:col-start-3">
                Remove
              </button>
            </li>
          ))}
        </ol>
        <button
          type="button"
          disabled={stats.length >= MAX_STATS}
          onClick={() => setStats([...stats, { key: 'RATING', route_index: null, value: null, label: null }])}
          className="mt-2 text-sm font-semibold text-brand-800 hover:text-brand-900 disabled:opacity-40"
        >
          + Add a stat
        </button>
        {errors.stats && <p className="mt-1 text-sm text-laterite-600">{errors.stats}</p>}
      </fieldset>

      {save.error && Object.keys(errors).length === 0 && <ErrorNote error={save.error} />}
      <Button type="submit" disabled={save.isPending}>
        {save.isPending ? 'Saving…' : 'Save record'}
      </Button>
    </form>
  )
}

function move<T>(list: T[], from: number, to: number): T[] {
  const next = [...list]
  const [item] = next.splice(from, 1)
  next.splice(to, 0, item)
  return next
}

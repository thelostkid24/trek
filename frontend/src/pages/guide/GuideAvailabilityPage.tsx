import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import {
  createAvailability,
  deleteAvailability,
  listOwnAvailability,
  updateAvailability,
  type AvailabilityRange,
} from '../../api/availability.ts'
import { fieldErrors } from '../../auth/errorMessages.ts'
import { useAuth } from '../../auth/useAuth.ts'
import { Button, ErrorNote, Loading, Panel } from '../../components/admin/AdminUi.tsx'
import { TextField } from '../../components/auth/TextField.tsx'
import { dateRange, monthKey, monthLabel, parseDate, todayIst } from '../../lib/format.ts'

const QUERY_KEY = ['guide-availability']
const EMPTY = { start_date: '', end_date: '', note: '' }

/**
 * /guide/availability (docs/TRD.md §7.21.7): the guide marks the days they're free to lead, so the team can plan
 * departures with them. Only the guide and admins see these; ranges never overlap and past ones drop off the list.
 * Rendered inside the guide layout.
 */
export function GuideAvailabilityPage() {
  const { withAuth } = useAuth()
  const queryClient = useQueryClient()
  const list = useQuery({ queryKey: QUERY_KEY, queryFn: () => withAuth(listOwnAvailability) })
  const [editing, setEditing] = useState<string | null>(null)
  const [form, setForm] = useState(EMPTY)

  const save = useMutation({
    mutationFn: () => {
      const body = { start_date: form.start_date, end_date: form.end_date || form.start_date, note: form.note.trim() || null }
      return withAuth((token) => (editing ? updateAvailability(token, editing, body) : createAvailability(token, body)))
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: QUERY_KEY })
      setEditing(null)
      setForm(EMPTY)
    },
  })
  const remove = useMutation({
    mutationFn: (id: string) => withAuth((token) => deleteAvailability(token, id)),
    onSuccess: (_, id) => {
      void queryClient.invalidateQueries({ queryKey: QUERY_KEY })
      if (editing === id) {
        setEditing(null)
        setForm(EMPTY)
      }
    },
  })
  const errors = fieldErrors(save.error)
  const set = (field: keyof typeof EMPTY) => (e: { target: { value: string } }) => setForm((f) => ({ ...f, [field]: e.target.value }))
  const edit = (r: AvailabilityRange) => {
    save.reset()
    setEditing(r.id)
    setForm({ start_date: r.start_date, end_date: r.end_date, note: r.note ?? '' })
  }
  const cancel = () => {
    save.reset()
    setEditing(null)
    setForm(EMPTY)
  }

  const items = list.data?.items ?? []
  const months = new Map<string, AvailabilityRange[]>()
  for (const r of items) {
    const key = monthKey(r.start_date)
    months.set(key, [...(months.get(key) ?? []), r])
  }

  return (
    <div className="mx-auto max-w-3xl space-y-5 px-5 py-6 sm:px-10">
      <Panel title={editing ? 'Change these dates' : 'Add dates you’re free'}>
        <p className="text-sm text-stone-600">
          Mark the days you could lead a trek. Only the Empty Valley team sees them. Dates and prices are set with the Empty
          Valley team.
        </p>
        <form
          onSubmit={(e) => {
            e.preventDefault()
            save.mutate()
          }}
          className="mt-4 space-y-4"
        >
          <div className="grid gap-4 sm:grid-cols-2">
            <TextField
              label="From"
              name="start_date"
              type="date"
              required
              min={todayIst()}
              value={form.start_date}
              onChange={set('start_date')}
              error={errors.start_date}
            />
            <TextField
              label="To"
              name="end_date"
              type="date"
              min={form.start_date || todayIst()}
              value={form.end_date}
              onChange={set('end_date')}
              error={errors.end_date}
              hint="Leave empty for a single day"
            />
          </div>
          <TextField
            label="Note for the team"
            name="note"
            maxLength={120}
            value={form.note}
            onChange={set('note')}
            error={errors.note}
            hint="Optional, e.g. “Happy to lead Kedarkantha”"
          />
          {save.error && Object.keys(errors).length === 0 && <ErrorNote error={save.error} />}
          <div className="flex flex-wrap gap-2">
            <Button type="submit" disabled={save.isPending || !form.start_date}>
              {save.isPending ? 'Saving…' : editing ? 'Save changes' : 'Add dates'}
            </Button>
            {editing && (
              <Button tone="secondary" onClick={cancel}>
                Cancel
              </Button>
            )}
          </div>
        </form>
      </Panel>

      <Panel title="When you’re free">
        {list.isPending ? (
          <Loading />
        ) : list.isError ? (
          <ErrorNote error={list.error} />
        ) : items.length === 0 ? (
          <p className="text-sm text-stone-600">No dates yet. Add the first range above.</p>
        ) : (
          <div className="space-y-5">
            <ErrorNote error={remove.error} />
            {[...months].map(([key, ranges]) => (
              <section key={key}>
                <h3 className="text-xs font-medium tracking-[0.16em] text-pine-600 uppercase">{monthLabel(key)}</h3>
                <ul className="mt-2 divide-y divide-stone-100">
                  {ranges.map((r) => (
                    <li key={r.id} className="flex flex-wrap items-center gap-x-4 gap-y-1 py-2.5">
                      <div className="min-w-0 flex-1">
                        <p className="font-medium text-stone-900">{dateRange(r.start_date, r.end_date)}</p>
                        <p className="text-sm text-stone-600">
                          {days(r)}
                          {r.note && ` · ${r.note}`}
                        </p>
                      </div>
                      <div className="flex gap-3 text-sm">
                        <button type="button" onClick={() => edit(r)} className="font-medium text-brand-800 hover:text-brand-950">
                          Edit
                        </button>
                        <button
                          type="button"
                          onClick={() => remove.mutate(r.id)}
                          disabled={remove.isPending}
                          aria-label={`Remove ${dateRange(r.start_date, r.end_date)}`}
                          className="text-stone-500 hover:text-laterite-600 disabled:opacity-50"
                        >
                          Remove
                        </button>
                      </div>
                    </li>
                  ))}
                </ul>
              </section>
            ))}
          </div>
        )}
      </Panel>
    </div>
  )
}

/** "1 day", "7 days". */
function days(r: AvailabilityRange): string {
  const n = Math.round((parseDate(r.end_date).getTime() - parseDate(r.start_date).getTime()) / 86_400_000) + 1
  return `${n} ${n === 1 ? 'day' : 'days'}`
}

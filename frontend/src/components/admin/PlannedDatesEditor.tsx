import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { listPlannedDates, replacePlannedDates, type PlannedDateInput } from '../../api/admin.ts'
import type { PlannedDate } from '../../api/catalog.ts'
import { fieldErrors } from '../../auth/errorMessages.ts'
import { useAuth } from '../../auth/useAuth.ts'
import { shortRange } from '../../lib/format.ts'
import { TextField } from '../auth/TextField.tsx'
import { Button, ErrorNote, Loading, Panel } from './AdminUi.tsx'

/**
 * A track's planned dates (docs/TRD.md §7.20): dates the trek runs before a guide is confirmed. The trek page shows
 * each as "Guide: to be updated", never bookable. Publishing a departure on the same start date replaces it there.
 */
export function PlannedDatesEditor({ trackId }: { trackId: string }) {
  const { withAuth } = useAuth()
  const queryKey = ['admin-planned-dates', trackId]
  const dates = useQuery({ queryKey, queryFn: () => withAuth((token) => listPlannedDates(token, trackId)) })

  if (dates.isPending) return <Loading />
  if (dates.isError) return <ErrorNote error={dates.error} />
  return <DatesForm key={JSON.stringify(dates.data.items)} trackId={trackId} saved={dates.data.items} queryKey={queryKey} />
}

const toInput = (d: PlannedDate): PlannedDateInput => ({ start_date: d.start_date, note: d.note })

function DatesForm({ trackId, saved, queryKey }: { trackId: string; saved: PlannedDate[]; queryKey: string[] }) {
  const { withAuth } = useAuth()
  const queryClient = useQueryClient()
  const [items, setItems] = useState<PlannedDateInput[]>(saved.map(toInput))
  const save = useMutation({
    mutationFn: (list: PlannedDateInput[]) => withAuth((token) => replacePlannedDates(token, trackId, list)),
    onSuccess: (data) => queryClient.setQueryData(queryKey, data),
  })
  const errors = fieldErrors(save.error)
  const dirty = JSON.stringify(items) !== JSON.stringify(saved.map(toInput))
  const update = (i: number, patch: Partial<PlannedDateInput>) =>
    setItems((list) => list.map((it, j) => (j === i ? { ...it, ...patch } : it)))

  return (
    <Panel
      title={`Planned dates (${items.length})`}
      action={
        <Button disabled={!dirty || save.isPending || items.some((d) => !d.start_date)} onClick={() => save.mutate(items)}>
          {save.isPending ? 'Saving…' : dirty ? 'Save' : 'Saved'}
        </Button>
      }
    >
      <p className="text-sm text-stone-600">
        Dates this trek runs whose guide isn't confirmed yet. The trek page shows them as “Guide: to be updated” and
        nobody can book them. Once a guide is set, add the departure under Departures and publish it; this date then
        drops off the page by itself. The end date follows from the trek's length.
      </p>
      <ol className="mt-4 space-y-3">
        {items.map((item, i) => {
          const savedRow = saved.find((d) => d.start_date === item.start_date)
          return (
            <li key={i} className="grid gap-3 rounded-xl border border-stone-200 p-3 sm:grid-cols-[12rem_1fr_auto] sm:items-end">
              <TextField label="Start date" name={`planned-${i}-start`} type="date" required value={item.start_date}
                onChange={(e) => update(i, { start_date: e.target.value })} error={errors[`items[${i}].start_date`]}
                hint={savedRow ? shortRange(savedRow.start_date, savedRow.end_date) : undefined} />
              <TextField label="Note (optional)" name={`planned-${i}-note`} maxLength={60} value={item.note ?? ''}
                onChange={(e) => update(i, { note: e.target.value || null })} error={errors[`items[${i}].note`]}
                hint="E.g. Christmas batch" />
              <button type="button" onClick={() => setItems((list) => list.filter((_, j) => j !== i))}
                className="pb-3 text-left text-xs font-medium text-laterite-600 hover:text-laterite-500">
                Remove
              </button>
            </li>
          )
        })}
      </ol>
      <div className="mt-3 flex flex-wrap items-center gap-3">
        <Button tone="secondary" disabled={items.length >= 40} onClick={() => setItems((list) => [...list, { start_date: '', note: null }])}>
          Add date
        </Button>
        {dirty && (
          <button type="button" onClick={() => setItems(saved.map(toInput))} className="text-sm text-stone-600 hover:text-stone-900">
            Undo changes
          </button>
        )}
      </div>
      {save.error && Object.keys(errors).length === 0 && (
        <div className="mt-3">
          <ErrorNote error={save.error} />
        </div>
      )}
    </Panel>
  )
}

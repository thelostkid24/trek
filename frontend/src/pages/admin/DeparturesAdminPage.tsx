import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import {
  CANCEL_REASON_LABEL,
  cancelDeparture,
  createDeparture,
  deleteDeparture,
  listAdminDepartures,
  listGuides,
  listTracks,
  publishDeparture,
  updateDeparture,
  type AdminDeparture,
  type CancelReason,
  type DepartureInput,
} from '../../api/admin.ts'
import { MAX_GROUP_SIZE } from '../../api/catalog.ts'
import { fieldErrors } from '../../auth/errorMessages.ts'
import { useAuth } from '../../auth/useAuth.ts'
import { Button, ErrorNote, Loading, Panel, StatusPill } from '../../components/admin/AdminUi.tsx'
import { TextField } from '../../components/auth/TextField.tsx'
import { SelectField, TextAreaField } from '../../components/profile/fields.tsx'
import { dateRange, rupees, todayIst } from '../../lib/format.ts'

const KEY = ['admin-departures']

export function DeparturesAdminPage() {
  const { withAuth } = useAuth()
  const departures = useQuery({ queryKey: KEY, queryFn: () => withAuth(listAdminDepartures) })
  const [editing, setEditing] = useState<AdminDeparture | 'new' | null>(null)

  return (
    <>
      {editing && (
        <DepartureForm
          key={editing === 'new' ? 'new' : editing.id}
          departure={editing === 'new' ? null : editing}
          onDone={() => setEditing(null)}
        />
      )}
      <Panel title="Departures" action={!editing && <Button onClick={() => setEditing('new')}>New departure</Button>}>
        {departures.isPending ? (
          <Loading />
        ) : departures.isError ? (
          <ErrorNote error={departures.error} />
        ) : departures.data.items.length === 0 ? (
          <p className="text-sm text-stone-600">No departures yet.</p>
        ) : (
          <ul className="divide-y divide-stone-100">
            {departures.data.items.map((d) => (
              <DepartureRow key={d.id} departure={d} onEdit={() => setEditing(d)} />
            ))}
          </ul>
        )}
      </Panel>
    </>
  )
}

function DepartureRow({ departure: d, onEdit }: { departure: AdminDeparture; onEdit: () => void }) {
  const { withAuth } = useAuth()
  const queryClient = useQueryClient()
  const [cancelling, setCancelling] = useState(false)
  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: KEY })
    void queryClient.invalidateQueries({ queryKey: ['public-departures'] })
  }
  const publish = useMutation({
    mutationFn: () => withAuth((token) => publishDeparture(token, d.id)),
    onSuccess: refresh,
  })
  const remove = useMutation({
    mutationFn: () => withAuth((token) => deleteDeparture(token, d.id)),
    onSuccess: refresh,
  })

  return (
    <li className="py-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="flex flex-wrap items-center gap-2 font-medium">
            {d.track.name} <StatusPill status={d.status} />
          </p>
          <p className="text-sm text-stone-600">
            {dateRange(d.start_date, d.end_date)} · {rupees(d.price_paise)} · {d.seats_taken}/{d.max_group_size} seats
          </p>
          <p className="text-sm text-stone-500">Guide: {d.guide.full_name ?? d.guide.email}</p>
          {d.status === 'CANCELLED' && d.cancel_reason_code && (
            <p className="mt-1 text-sm text-laterite-600">
              {CANCEL_REASON_LABEL[d.cancel_reason_code]}: {d.cancel_reason_note}
            </p>
          )}
        </div>
        <div className="flex flex-wrap gap-2">
          {d.status === 'DRAFT' && (
            <>
              <Button tone="secondary" onClick={onEdit}>
                Edit
              </Button>
              <Button
                tone="secondary"
                disabled={remove.isPending}
                onClick={() => window.confirm('Delete this draft?') && remove.mutate()}
              >
                Delete
              </Button>
              <Button
                disabled={publish.isPending}
                onClick={() =>
                  window.confirm('Publish this departure? It becomes bookable and can no longer be edited.') &&
                  publish.mutate()
                }
              >
                {publish.isPending ? 'Publishing…' : 'Publish'}
              </Button>
            </>
          )}
          {d.status === 'PUBLISHED' && !cancelling && d.start_date > todayIst() && (
            <Button tone="danger" onClick={() => setCancelling(true)}>
              Cancel departure
            </Button>
          )}
        </div>
      </div>
      <div className="mt-2 space-y-2">
        <ErrorNote error={publish.error ?? remove.error} />
        {cancelling && <CancelForm departure={d} onDone={() => setCancelling(false)} onCancelled={refresh} />}
      </div>
    </li>
  )
}

function CancelForm({
  departure,
  onDone,
  onCancelled,
}: {
  departure: AdminDeparture
  onDone: () => void
  onCancelled: () => void
}) {
  const { withAuth } = useAuth()
  const [reason, setReason] = useState<CancelReason | ''>('')
  const [note, setNote] = useState('')
  const cancel = useMutation({
    mutationFn: () =>
      withAuth((token) =>
        cancelDeparture(token, departure.id, { reason_code: reason as CancelReason, reason_note: note.trim() }),
      ),
    onSuccess: () => {
      onCancelled()
      onDone()
    },
  })
  const errors = fieldErrors(cancel.error)

  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (window.confirm('Cancel this departure for force majeure? Every paid trekker is refunded in full.')) {
      cancel.mutate()
    }
  }

  return (
    <form onSubmit={submit} className="space-y-3 rounded-xl bg-laterite-100/50 p-4">
      <p className="text-sm text-stone-700">
        Only force majeure can cancel a departure. Low bookings are never a reason — the trek runs.
      </p>
      <SelectField
        label="Reason"
        name={`reason-${departure.id}`}
        required
        value={reason}
        onChange={(e) => setReason(e.target.value as CancelReason | '')}
        error={errors.reason_code}
        options={Object.entries(CANCEL_REASON_LABEL).map(([value, label]) => ({ value, label }))}
      />
      <TextAreaField
        label="What happened (shown to trekkers)"
        name={`note-${departure.id}`}
        required
        maxLength={1000}
        value={note}
        onChange={(e) => setNote(e.target.value)}
        error={errors.reason_note}
      />
      {cancel.error && Object.keys(errors).length === 0 && <ErrorNote error={cancel.error} />}
      <div className="flex gap-2">
        <Button type="submit" tone="danger" disabled={cancel.isPending || !reason || !note.trim()}>
          {cancel.isPending ? 'Cancelling…' : 'Cancel and refund'}
        </Button>
        <Button tone="secondary" onClick={onDone}>
          Keep departure
        </Button>
      </div>
    </form>
  )
}

function DepartureForm({ departure, onDone }: { departure: AdminDeparture | null; onDone: () => void }) {
  const { withAuth } = useAuth()
  const queryClient = useQueryClient()
  const tracks = useQuery({ queryKey: ['admin-tracks'], queryFn: () => withAuth(listTracks) })
  const guides = useQuery({ queryKey: ['admin-guides'], queryFn: () => withAuth(listGuides) })
  const [trackId, setTrackId] = useState(departure?.track.id ?? '')
  const [guideId, setGuideId] = useState(departure?.guide.id ?? '')
  const [startDate, setStartDate] = useState(departure?.start_date ?? '')
  const [priceRupees, setPriceRupees] = useState(departure ? String(departure.price_paise / 100) : '')
  const [groupSize, setGroupSize] = useState(String(departure?.max_group_size ?? MAX_GROUP_SIZE))

  const save = useMutation({
    mutationFn: (body: DepartureInput) =>
      withAuth((token) => (departure ? updateDeparture(token, departure.id, body) : createDeparture(token, body))),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: KEY })
      onDone()
    },
  })
  const errors = fieldErrors(save.error)

  const submit = (e: FormEvent) => {
    e.preventDefault()
    save.mutate({
      track_id: trackId,
      guide_id: guideId,
      start_date: startDate,
      price_paise: Math.round(Number(priceRupees) * 100),
      max_group_size: Number(groupSize),
    })
  }

  if (tracks.isPending || guides.isPending) {
    return (
      <Panel title="New departure">
        <Loading />
      </Panel>
    )
  }
  if (tracks.isError || guides.isError) {
    return (
      <Panel title="New departure">
        <ErrorNote error={tracks.error ?? guides.error} />
      </Panel>
    )
  }

  return (
    <Panel title={departure ? `Edit ${departure.track.name}` : 'New departure'}>
      {tracks.data.items.length === 0 || guides.data.items.length === 0 ? (
        <p className="text-sm text-stone-600">Add at least one track and one guide first.</p>
      ) : (
        <form onSubmit={submit} className="grid gap-4 sm:grid-cols-2">
          <SelectField
            label="Track"
            name="track_id"
            required
            value={trackId}
            onChange={(e) => setTrackId(e.target.value)}
            error={errors.track_id}
            options={tracks.data.items.map((t) => ({
              value: t.id,
              label: `${t.name} (${t.duration_days} ${t.duration_days === 1 ? 'day' : 'days'})`,
            }))}
          />
          <SelectField
            label="Guide"
            name="guide_id"
            required
            value={guideId}
            onChange={(e) => setGuideId(e.target.value)}
            error={errors.guide_id}
            options={guides.data.items.map((g) => ({ value: g.id, label: g.full_name ?? g.email ?? g.id }))}
          />
          <TextField
            label="Start date"
            name="start_date"
            type="date"
            required
            min={todayIst()}
            value={startDate}
            onChange={(e) => setStartDate(e.target.value)}
            error={errors.start_date}
          />
          <TextField
            label="Price per seat"
            name="price"
            type="number"
            required
            prefix="₹"
            min={100}
            max={100000}
            step="1"
            value={priceRupees}
            onChange={(e) => setPriceRupees(e.target.value)}
            error={errors.price_paise}
          />
          <SelectField
            label="Batch size"
            name="max_group_size"
            required
            value={groupSize}
            onChange={(e) => setGroupSize(e.target.value)}
            error={errors.max_group_size}
            options={Array.from({ length: MAX_GROUP_SIZE }, (_, i) => i + 1).map((n) => ({ value: String(n), label: `${n} ${n === 1 ? 'seat' : 'seats'}` }))}
          />
          {save.error && Object.keys(errors).length === 0 && (
            <div className="sm:col-span-2">
              <ErrorNote error={save.error} />
            </div>
          )}
          <div className="flex gap-2 sm:col-span-2">
            <Button type="submit" disabled={save.isPending}>
              {save.isPending ? 'Saving…' : 'Save draft'}
            </Button>
            <Button tone="secondary" onClick={onDone}>
              Cancel
            </Button>
          </div>
        </form>
      )}
    </Panel>
  )
}

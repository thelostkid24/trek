import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useRef, useState, type FormEvent } from 'react'
import {
  createTrack,
  deleteTrackPhoto,
  listTracks,
  setTrackListed,
  updateTrack,
  uploadTrackPhoto,
  type Track,
  type TrackInput,
} from '../../api/admin.ts'
import type { Items } from '../../api/catalog.ts'
import { DIFFICULTY_LABEL, type Difficulty } from '../../api/catalog.ts'
import { fieldErrors } from '../../auth/errorMessages.ts'
import { useAuth } from '../../auth/useAuth.ts'
import { Button, ErrorNote, Loading, Panel } from '../../components/admin/AdminUi.tsx'
import { TextField } from '../../components/auth/TextField.tsx'
import { DifficultyPill } from '../../components/catalog/DeparturePieces.tsx'
import { SelectField, TextAreaField } from '../../components/profile/fields.tsx'
import { shrinkPhoto } from '../../lib/photos.ts'

const EMPTY: TrackInput = {
  slug: '',
  name: '',
  region: '',
  difficulty: 'EASY',
  duration_days: 1,
  max_altitude_m: null,
  summary: '',
  description: '',
  meeting_point: '',
  distance_km: null,
  base_altitude_m: null,
  highest_camp_m: null,
  stay: null,
  season_label: null,
  itinerary: [],
}

const numberOrNull = (value: string) => (value === '' ? null : Number(value))
const textOrNull = (value: string) => (value === '' ? null : value)

export function TracksAdminPage() {
  const { withAuth } = useAuth()
  const tracks = useQuery({ queryKey: ['admin-tracks'], queryFn: () => withAuth(listTracks) })
  // null = form closed; 'new' = create; otherwise the track being edited.
  const [editing, setEditing] = useState<Track | 'new' | null>(null)

  return (
    <>
      {editing ? (
        <TrackForm
          key={editing === 'new' ? 'new' : editing.id}
          track={editing === 'new' ? null : editing}
          onDone={() => setEditing(null)}
        />
      ) : null}
      {editing && editing !== 'new' ? <TrackPhotos trackId={editing.id} /> : null}

      <Panel
        title="Tracks"
        action={
          !editing && (
            <Button onClick={() => setEditing('new')}>New track</Button>
          )
        }
      >
        {tracks.isPending ? (
          <Loading />
        ) : tracks.isError ? (
          <ErrorNote error={tracks.error} />
        ) : tracks.data.items.length === 0 ? (
          <p className="text-sm text-stone-600">No tracks yet. Add one to start scheduling departures.</p>
        ) : (
          <ul className="divide-y divide-stone-100">
            {tracks.data.items.map((t) => (
              <li key={t.id} className="flex items-center justify-between gap-3 py-3">
                <div className="min-w-0">
                  <p className="flex items-center gap-2 font-medium">
                    <span className="truncate">{t.name}</span>
                    <DifficultyPill difficulty={t.difficulty} />
                  </p>
                  <p className="truncate text-sm text-stone-500">
                    {t.region} · {t.duration_days} {t.duration_days === 1 ? 'day' : 'days'} · {t.slug}
                  </p>
                </div>
                <div className="flex shrink-0 items-center gap-3">
                  <ListedSwitch track={t} />
                  <Button tone="secondary" onClick={() => setEditing(t)}>
                    Edit
                  </Button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </Panel>
    </>
  )
}

/**
 * Shows a track in the public catalog while it has no dates. A track with an upcoming published
 * departure is in the catalog either way, so the switch reads as "even without dates" there.
 */
function ListedSwitch({ track }: { track: Track }) {
  const { withAuth } = useAuth()
  const queryClient = useQueryClient()
  const save = useMutation({
    mutationFn: (listed: boolean) => withAuth((token) => setTrackListed(token, track.id, listed)),
    onSuccess: (updated) =>
      queryClient.setQueryData(['admin-tracks'], (current: Items<Track> | undefined) =>
        current ? { items: current.items.map((t) => (t.id === updated.id ? updated : t)) } : current,
      ),
  })

  return (
    <label className="flex cursor-pointer items-center gap-2 text-sm text-stone-600" title="Show in the public catalog">
      <input
        type="checkbox"
        checked={track.listed}
        disabled={save.isPending}
        onChange={(e) => save.mutate(e.target.checked)}
        className="size-4 accent-brand-800"
      />
      <span className="hidden sm:inline">In catalog</span>
    </label>
  )
}

function TrackForm({ track, onDone }: { track: Track | null; onDone: () => void }) {
  const { withAuth } = useAuth()
  const queryClient = useQueryClient()
  const [form, setForm] = useState<TrackInput>(() => {
    if (!track) return EMPTY
    const { id: _id, photos: _p, created_at: _c, updated_at: _u, ...input } = track
    return input
  })
  // One line per day; kept apart from `form` so changing the duration doesn't lose what's typed.
  const [days, setDays] = useState<string[]>(() => track?.itinerary ?? [])
  const save = useMutation({
    mutationFn: (body: TrackInput) =>
      withAuth((token) => (track ? updateTrack(token, track.id, body) : createTrack(token, body))),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin-tracks'] })
      onDone()
    },
  })
  const errors = fieldErrors(save.error)
  const set = <K extends keyof TrackInput>(key: K, value: TrackInput[K]) => setForm((f) => ({ ...f, [key]: value }))

  const submit = (e: FormEvent) => {
    e.preventDefault()
    const lines = Array.from({ length: form.duration_days }, (_, i) => (days[i] ?? '').trim())
    // All blank = no itinerary yet; otherwise every day needs a line (the server says which one is missing).
    save.mutate({ ...form, itinerary: lines.every((l) => l === '') ? [] : lines })
  }
  const setDay = (index: number, value: string) =>
    setDays((current) => {
      const next = [...current]
      next[index] = value
      return next
    })

  return (
    <Panel title={track ? `Edit ${track.name}` : 'New track'}>
      <form onSubmit={submit} className="grid gap-4 sm:grid-cols-2">
        <TextField label="Name" name="name" required maxLength={100} value={form.name}
          onChange={(e) => set('name', e.target.value)} error={errors.name} />
        <TextField label="Slug" name="slug" required maxLength={80} value={form.slug}
          onChange={(e) => set('slug', e.target.value.toLowerCase())} error={errors.slug}
          hint="Lowercase words joined by hyphens, e.g. rajmachi-fort" />
        <TextField label="Region" name="region" required maxLength={100} value={form.region}
          onChange={(e) => set('region', e.target.value)} error={errors.region} />
        <SelectField label="Difficulty" name="difficulty" required value={form.difficulty}
          onChange={(e) => set('difficulty', e.target.value as Difficulty)} error={errors.difficulty}
          options={Object.entries(DIFFICULTY_LABEL).map(([value, label]) => ({ value, label }))} />
        <TextField label="Duration (days)" name="duration_days" type="number" required min={1} max={7}
          value={form.duration_days} onChange={(e) => set('duration_days', Number(e.target.value))}
          error={errors.duration_days} />
        <TextField label="Highest point (m)" name="max_altitude_m" type="number" min={1} max={9000}
          value={form.max_altitude_m ?? ''}
          onChange={(e) => set('max_altitude_m', e.target.value === '' ? null : Number(e.target.value))}
          error={errors.max_altitude_m} />
        <TextField label="Trailhead altitude (m)" name="base_altitude_m" type="number" min={1} max={9000}
          value={form.base_altitude_m ?? ''} onChange={(e) => set('base_altitude_m', numberOrNull(e.target.value))}
          error={errors.base_altitude_m} hint="With the highest point, this gives the altitude gain." />
        <TextField label="Highest camp (m)" name="highest_camp_m" type="number" min={1} max={9000}
          value={form.highest_camp_m ?? ''} onChange={(e) => set('highest_camp_m', numberOrNull(e.target.value))}
          error={errors.highest_camp_m} />
        <TextField label="Trek distance (km)" name="distance_km" type="number" min={0.1} max={999.9} step={0.1}
          value={form.distance_km ?? ''} onChange={(e) => set('distance_km', numberOrNull(e.target.value))}
          error={errors.distance_km} hint="On foot, start to finish." />
        <TextField label="Stay" name="stay" maxLength={120} value={form.stay ?? ''}
          onChange={(e) => set('stay', textOrNull(e.target.value))} error={errors.stay}
          hint="E.g. Tents · twin share" />
        <div className="sm:col-span-2">
          <TextField label="Season" name="season_label" maxLength={60} value={form.season_label ?? ''}
            onChange={(e) => set('season_label', textOrNull(e.target.value))} error={errors.season_label}
            hint="Shown on the trek photo, e.g. Snow trek · Dec–Apr" />
        </div>
        <div className="sm:col-span-2">
          <TextField label="Meeting point" name="meeting_point" required maxLength={300} value={form.meeting_point}
            onChange={(e) => set('meeting_point', e.target.value)} error={errors.meeting_point} />
        </div>
        <div className="sm:col-span-2">
          <TextAreaField label="Summary" name="summary" required maxLength={200} rows={2} value={form.summary}
            onChange={(e) => set('summary', e.target.value)} error={errors.summary} />
        </div>
        <div className="sm:col-span-2">
          <TextAreaField label="Description" name="description" required maxLength={5000} rows={6}
            value={form.description} onChange={(e) => set('description', e.target.value)}
            error={errors.description} />
        </div>
        <fieldset className="space-y-2 sm:col-span-2">
          <legend className="text-sm font-medium text-stone-800">Itinerary</legend>
          <p className="text-xs text-stone-500">One line per day, e.g. "Sankri to Juda ka Talab, 2,770 m". Leave all blank to skip.</p>
          {errors.itinerary && <p className="text-sm text-laterite-600">{errors.itinerary}</p>}
          {Array.from({ length: form.duration_days }, (_, i) => (
            <TextField key={i} label={`Day ${i + 1}`} name={`itinerary-${i}`} maxLength={200} value={days[i] ?? ''}
              onChange={(e) => setDay(i, e.target.value)} error={errors[`itinerary[${i}]`]} />
          ))}
        </fieldset>
        {save.error && Object.keys(errors).length === 0 && (
          <div className="sm:col-span-2">
            <ErrorNote error={save.error} />
          </div>
        )}
        <div className="flex gap-2 sm:col-span-2">
          <Button type="submit" disabled={save.isPending}>
            {save.isPending ? 'Saving…' : 'Save track'}
          </Button>
          <Button tone="secondary" onClick={onDone}>
            Cancel
          </Button>
        </div>
      </form>
    </Panel>
  )
}

const MAX_PHOTOS = 30

/** Photos from past runs, shown on the trek page. Uploads save straight away, apart from the track form. */
function TrackPhotos({ trackId }: { trackId: string }) {
  const { withAuth } = useAuth()
  const queryClient = useQueryClient()
  // Read from the list query, not the form's snapshot, so new uploads appear.
  const tracks = useQuery({ queryKey: ['admin-tracks'], queryFn: () => withAuth(listTracks) })
  const photos = tracks.data?.items.find((t) => t.id === trackId)?.photos ?? []
  const [caption, setCaption] = useState('')
  const [progress, setProgress] = useState<string | null>(null)
  const input = useRef<HTMLInputElement>(null)
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['admin-tracks'] })

  const upload = useMutation({
    mutationFn: async (files: File[]) => {
      for (const [i, file] of files.entries()) {
        setProgress(files.length > 1 ? `Uploading ${i + 1} of ${files.length}…` : 'Uploading…')
        const blob = await shrinkPhoto(file).catch(() => file)
        await withAuth((token) => uploadTrackPhoto(token, trackId, blob, caption))
      }
    },
    onSuccess: () => setCaption(''),
    onSettled: () => {
      setProgress(null)
      if (input.current) input.current.value = ''
      void refresh()
    },
  })
  const remove = useMutation({
    mutationFn: (photoId: string) => withAuth((token) => deleteTrackPhoto(token, trackId, photoId)),
    onSettled: () => void refresh(),
  })
  const full = photos.length >= MAX_PHOTOS
  const captionError = fieldErrors(upload.error).caption

  return (
    <Panel title={`Photos (${photos.length} of ${MAX_PHOTOS})`}>
      <p className="text-sm text-stone-600">
        Views and moments from past departures. They show on the trek page in the order you add them; the first one
        leads.
      </p>
      {photos.length > 0 && (
        <ul className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
          {photos.map((p) => (
            <li key={p.id} className="overflow-hidden rounded-xl ring-1 ring-stone-200">
              <img src={p.url} alt={p.caption ?? ''} loading="lazy" className="aspect-[4/3] w-full object-cover" />
              <div className="flex items-center justify-between gap-2 px-2.5 py-2">
                <span className="truncate text-xs text-stone-600">{p.caption ?? 'No caption'}</span>
                <button
                  type="button"
                  disabled={remove.isPending}
                  onClick={() => window.confirm('Delete this photo?') && remove.mutate(p.id)}
                  className="shrink-0 text-xs font-medium text-laterite-600 hover:text-laterite-500 disabled:opacity-50"
                >
                  Delete
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
      <div className="mt-5 grid gap-3 sm:grid-cols-[1fr_auto] sm:items-end">
        <TextField label="Caption (optional)" name="photo_caption" maxLength={200} value={caption}
          onChange={(e) => setCaption(e.target.value)} error={captionError}
          hint="Applies to every photo in this upload." />
        <div>
          <input
            ref={input}
            type="file"
            accept="image/jpeg,image/png"
            multiple
            className="sr-only"
            id="track-photo-input"
            disabled={full || upload.isPending}
            onChange={(e) => {
              const files = Array.from(e.target.files ?? []).slice(0, MAX_PHOTOS - photos.length)
              if (files.length > 0) upload.mutate(files)
            }}
          />
          <Button disabled={full || upload.isPending} onClick={() => input.current?.click()}>
            {progress ?? 'Add photos'}
          </Button>
        </div>
      </div>
      {full && <p className="mt-3 text-sm text-stone-600">That's the limit. Delete one to add another.</p>}
      {upload.error && !captionError && (
        <div className="mt-3">
          <ErrorNote error={upload.error} />
        </div>
      )}
      {remove.error && (
        <div className="mt-3">
          <ErrorNote error={remove.error} />
        </div>
      )}
    </Panel>
  )
}

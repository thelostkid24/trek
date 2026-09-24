import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useRef, useState, type FormEvent } from 'react'
import {
  createTrack,
  deleteTrackPhoto,
  describeTrackPhoto,
  listTracks,
  setTrackListed,
  updateTrack,
  uploadTrackPhoto,
  type ItineraryDayInput,
  type PhotoWords,
  type Track,
  type TrackInput,
} from '../../api/admin.ts'
import type { Items, TrackPhoto } from '../../api/catalog.ts'
import { DIFFICULTY_LABEL, type Difficulty } from '../../api/catalog.ts'
import { fieldErrors } from '../../auth/errorMessages.ts'
import { useAuth } from '../../auth/useAuth.ts'
import { Button, ErrorNote, Loading, Panel } from '../../components/admin/AdminUi.tsx'
import { ContentEditor } from '../../components/admin/ContentEditor.tsx'
import { SnowReports } from '../../components/admin/SnowReports.tsx'
import { TextField } from '../../components/auth/TextField.tsx'
import { DifficultyPill } from '../../components/catalog/DeparturePieces.tsx'
import { SelectField, TextAreaField } from '../../components/profile/fields.tsx'
import { toFeet, toMetres } from '../../lib/format.ts'
import { shrinkPhoto } from '../../lib/photos.ts'

// Altitudes are stored in metres and typed in feet; the form keeps what was typed until it's saved.
const ftText = (m: number | null) => (m === null ? '' : String(toFeet(m)))
const metresOrNull = (ft: string) => (ft.trim() === '' ? null : toMetres(Number(ft)))
const numberOrNull = (value: string) => (value.trim() === '' ? null : Number(value))
const textOrNull = (value: string) => (value.trim() === '' ? null : value)

type TrackFields = Omit<TrackInput, 'itinerary' | 'max_altitude_m' | 'base_altitude_m' | 'highest_camp_m'>
type Altitudes = { max: string; base: string; camp: string }
type DayDraft = {
  summary: string
  description: string
  distance_km: string
  start_ft: string
  high_ft: string
  end_ft: string
  hours_min: string
  hours_max: string
  route_note: string
}

const EMPTY: TrackFields = {
  slug: '',
  name: '',
  region: '',
  difficulty: 'EASY',
  duration_days: 1,
  summary: '',
  description: '',
  meeting_point: '',
  distance_km: null,
  stay: null,
  season_label: null,
  pickup_drop: null,
  cloakroom: null,
  offloading: null,
  offloading_price_paise: null,
}

const EMPTY_DAY: DayDraft = {
  summary: '',
  description: '',
  distance_km: '',
  start_ft: '',
  high_ft: '',
  end_ft: '',
  hours_min: '',
  hours_max: '',
  route_note: '',
}

const SECTIONS = [
  { id: 'details', label: 'Details' },
  { id: 'photos', label: 'Photos' },
  { id: 'content', label: 'Page lists' },
  { id: 'snow', label: 'Snow reports' },
] as const
type Section = (typeof SECTIONS)[number]['id']

export function TracksAdminPage() {
  const { withAuth } = useAuth()
  const tracks = useQuery({ queryKey: ['admin-tracks'], queryFn: () => withAuth(listTracks) })
  // null = list; 'new' = create; otherwise the track being edited.
  const [editing, setEditing] = useState<Track | 'new' | null>(null)
  const [section, setSection] = useState<Section>('details')

  if (editing === 'new') return <TrackForm track={null} onDone={() => setEditing(null)} />
  if (editing) {
    return (
      <>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <button type="button" onClick={() => setEditing(null)} className="text-sm text-brand-700 hover:text-brand-900">
            ← All tracks
          </button>
          <a href={`/treks/${editing.slug}`} target="_blank" rel="noreferrer" className="text-sm text-brand-700 hover:text-brand-900">
            View trek page ↗
          </a>
        </div>
        <h2 className="font-display text-xl font-semibold">{editing.name}</h2>
        <nav className="flex gap-1 overflow-x-auto rounded-full bg-stone-100 p-1 text-sm" aria-label="Track sections">
          {SECTIONS.map((s) => (
            <button
              key={s.id}
              type="button"
              aria-current={section === s.id}
              onClick={() => setSection(s.id)}
              className={`rounded-full px-4 py-1.5 font-medium whitespace-nowrap ${section === s.id ? 'bg-white text-stone-900 shadow-sm' : 'text-stone-600 hover:text-stone-900'}`}
            >
              {s.label}
            </button>
          ))}
        </nav>
        {section === 'details' && <TrackForm key={editing.id} track={editing} onDone={() => setEditing(null)} />}
        {section === 'photos' && <TrackPhotos track={editing} />}
        {section === 'content' && <ContentEditor trackId={editing.id} />}
        {section === 'snow' && <SnowReports area="admin" trackId={editing.id} place={editing.meeting_point} />}
      </>
    )
  }

  return (
    <Panel title="Tracks" action={<Button onClick={() => setEditing('new')}>New track</Button>}>
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
                <Button
                  tone="secondary"
                  onClick={() => {
                    setSection('details')
                    setEditing(t)
                  }}
                >
                  Edit
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </Panel>
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

const YES_NO = [
  { value: 'true', label: 'Available' },
  { value: 'false', label: 'Not available' },
]
const boolText = (b: boolean | null) => (b === null ? '' : String(b))
const textBool = (v: string) => (v === '' ? null : v === 'true')

function TrackForm({ track, onDone }: { track: Track | null; onDone: () => void }) {
  const { withAuth } = useAuth()
  const queryClient = useQueryClient()
  const [form, setForm] = useState<TrackFields>(() => {
    if (!track) return EMPTY
    const {
      id: _id, photos: _p, listed: _l, itinerary: _i, created_at: _c, updated_at: _u,
      max_altitude_m: _m, base_altitude_m: _b, highest_camp_m: _h, ...fields
    } = track
    return fields
  })
  const [alt, setAlt] = useState<Altitudes>(() => ({
    max: ftText(track?.max_altitude_m ?? null),
    base: ftText(track?.base_altitude_m ?? null),
    camp: ftText(track?.highest_camp_m ?? null),
  }))
  const [price, setPrice] = useState(() => (track?.offloading_price_paise ? String(track.offloading_price_paise / 100) : ''))
  // One entry per day; kept apart from `form` so changing the duration doesn't lose what's typed.
  const [days, setDays] = useState<DayDraft[]>(() =>
    (track?.itinerary ?? []).map((d) => ({
      summary: d.summary,
      description: d.description ?? '',
      distance_km: d.distance_km === null ? '' : String(d.distance_km),
      start_ft: ftText(d.start_altitude_m),
      high_ft: ftText(d.high_altitude_m),
      end_ft: ftText(d.end_altitude_m),
      hours_min: d.hours_min === null ? '' : String(d.hours_min),
      hours_max: d.hours_max === null ? '' : String(d.hours_max),
      route_note: d.route_note ?? '',
    })),
  )
  const save = useMutation({
    mutationFn: (body: TrackInput) =>
      withAuth((token) => (track ? updateTrack(token, track.id, body) : createTrack(token, body))),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin-tracks'] })
      onDone()
    },
  })
  const errors = fieldErrors(save.error)
  const set = <K extends keyof TrackFields>(key: K, value: TrackFields[K]) => setForm((f) => ({ ...f, [key]: value }))
  const dayAt = (i: number) => days[i] ?? EMPTY_DAY
  const setDay = (i: number, patch: Partial<DayDraft>) =>
    setDays((current) => {
      const next = Array.from({ length: Math.max(current.length, i + 1) }, (_, j) => current[j] ?? EMPTY_DAY)
      next[i] = { ...next[i], ...patch }
      return next
    })

  const submit = (e: FormEvent) => {
    e.preventDefault()
    const drafts = Array.from({ length: form.duration_days }, (_, i) => dayAt(i))
    // All blank = no itinerary yet; otherwise every day needs a heading (the server says which one is missing).
    const blank = drafts.every((d) => Object.values(d).every((v) => v.trim() === ''))
    const itinerary: ItineraryDayInput[] = blank
      ? []
      : drafts.map((d) => ({
          summary: d.summary.trim(),
          description: textOrNull(d.description),
          distance_km: numberOrNull(d.distance_km),
          start_altitude_m: metresOrNull(d.start_ft),
          high_altitude_m: metresOrNull(d.high_ft),
          end_altitude_m: metresOrNull(d.end_ft),
          hours_min: numberOrNull(d.hours_min),
          hours_max: numberOrNull(d.hours_max),
          route_note: textOrNull(d.route_note),
        }))
    save.mutate({
      ...form,
      max_altitude_m: metresOrNull(alt.max),
      base_altitude_m: metresOrNull(alt.base),
      highest_camp_m: metresOrNull(alt.camp),
      offloading_price_paise: form.offloading && price.trim() !== '' ? Math.round(Number(price) * 100) : null,
      itinerary,
    })
  }

  return (
    <Panel title={track ? 'Details' : 'New track'}>
      <form onSubmit={submit} className="grid gap-4 sm:grid-cols-2">
        <TextField label="Name" name="name" required maxLength={100} value={form.name}
          onChange={(e) => set('name', e.target.value)} error={errors.name} />
        <TextField label="Slug" name="slug" required maxLength={80} value={form.slug}
          onChange={(e) => set('slug', e.target.value.toLowerCase())} error={errors.slug}
          hint="Lowercase words joined by hyphens, e.g. kedarkantha" />
        <TextField label="Region" name="region" required maxLength={100} value={form.region}
          onChange={(e) => set('region', e.target.value)} error={errors.region} />
        <SelectField label="Difficulty" name="difficulty" required value={form.difficulty}
          onChange={(e) => set('difficulty', e.target.value as Difficulty)} error={errors.difficulty}
          options={Object.entries(DIFFICULTY_LABEL).map(([value, label]) => ({ value, label }))} />
        <TextField label="Duration (days)" name="duration_days" type="number" required min={1} max={7}
          value={form.duration_days} onChange={(e) => set('duration_days', Number(e.target.value))}
          error={errors.duration_days} />
        <TextField label="Maximum altitude" name="max_altitude_ft" type="number" min={1} max={29000} suffix="ft"
          value={alt.max} onChange={(e) => setAlt((a) => ({ ...a, max: e.target.value }))} error={errors.max_altitude_m} />
        <TextField label="Trailhead altitude" name="base_altitude_ft" type="number" min={1} max={29000} suffix="ft"
          value={alt.base} onChange={(e) => setAlt((a) => ({ ...a, base: e.target.value }))}
          error={errors.base_altitude_m} hint="With the maximum, this gives the altitude gain." />
        <TextField label="Highest camp" name="highest_camp_ft" type="number" min={1} max={29000} suffix="ft"
          value={alt.camp} onChange={(e) => setAlt((a) => ({ ...a, camp: e.target.value }))} error={errors.highest_camp_m} />
        <TextField label="Trek distance" name="distance_km" type="number" min={0.1} max={999.9} step={0.1} suffix="km"
          value={form.distance_km ?? ''} onChange={(e) => set('distance_km', numberOrNull(e.target.value))}
          error={errors.distance_km} hint="On foot, start to finish." />
        <TextField label="Stay" name="stay" maxLength={120} value={form.stay ?? ''}
          onChange={(e) => set('stay', textOrNull(e.target.value))} error={errors.stay}
          hint="E.g. Tents · twin share" />
        <TextField label="Season" name="season_label" maxLength={60} value={form.season_label ?? ''}
          onChange={(e) => set('season_label', textOrNull(e.target.value))} error={errors.season_label}
          hint="Shown on the trek photo, e.g. Snow trek · Dec–Apr" />
        <TextField label="Meeting point" name="meeting_point" required maxLength={300} value={form.meeting_point}
          onChange={(e) => set('meeting_point', e.target.value)} error={errors.meeting_point}
          hint="Sent to trekkers with their booking." />

        <fieldset className="grid gap-4 rounded-xl border border-stone-200 p-4 sm:col-span-2 sm:grid-cols-2">
          <legend className="px-1 text-sm font-medium text-stone-800">Services on the trek page</legend>
          <TextField label="Pickup and drop" name="pickup_drop" maxLength={120} value={form.pickup_drop ?? ''}
            onChange={(e) => set('pickup_drop', textOrNull(e.target.value))} error={errors.pickup_drop}
            hint="E.g. Sankri to Sankri" />
          <SelectField label="Cloakroom" name="cloakroom" placeholder="Not stated" value={boolText(form.cloakroom)}
            onChange={(e) => set('cloakroom', textBool(e.target.value))} options={YES_NO} />
          <SelectField label="Offloading (paid)" name="offloading" placeholder="Not stated" value={boolText(form.offloading)}
            onChange={(e) => set('offloading', textBool(e.target.value))} options={YES_NO} />
          <TextField label="Offloading price" name="offloading_price" type="number" min={1} step={1} prefix="₹"
            disabled={!form.offloading} value={price} onChange={(e) => setPrice(e.target.value)}
            error={errors.offloading_price_paise} hint="Leave blank until it's fixed; the page says “Available · paid”." />
        </fieldset>

        <div className="sm:col-span-2">
          <TextAreaField label="Summary" name="summary" required maxLength={200} rows={2} value={form.summary}
            onChange={(e) => set('summary', e.target.value)} error={errors.summary} hint="Shown on catalog cards." />
        </div>
        <div className="sm:col-span-2">
          <TextAreaField label="Overview" name="description" required maxLength={5000} rows={8}
            value={form.description} onChange={(e) => set('description', e.target.value)}
            error={errors.description} hint="Leave a blank line between paragraphs." />
        </div>

        <fieldset className="space-y-3 sm:col-span-2">
          <legend className="text-sm font-medium text-stone-800">Day by day</legend>
          <p className="text-xs text-stone-500">
            A heading per day, then whatever's known. Altitudes run start → high point → end; the chart uses the higher
            of high point and end. Leave every day blank to skip.
          </p>
          {errors.itinerary && <p className="text-sm text-laterite-600">{errors.itinerary}</p>}
          {Array.from({ length: form.duration_days }, (_, i) => (
            <DayFields key={i} index={i} day={dayAt(i)} onChange={(patch) => setDay(i, patch)} errors={errors} />
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

function DayFields({
  index: i,
  day,
  onChange,
  errors,
}: {
  index: number
  day: DayDraft
  onChange: (patch: Partial<DayDraft>) => void
  errors: Record<string, string>
}) {
  const e = (field: string) => errors[`itinerary[${i}].${field}`]
  return (
    <div className="grid gap-3 rounded-xl border border-stone-200 p-4 sm:grid-cols-6">
      <div className="sm:col-span-6">
        <TextField label={`Day ${i + 1}`} name={`day-${i}-summary`} maxLength={200} value={day.summary}
          onChange={(ev) => onChange({ summary: ev.target.value })} error={e('summary')} hint="E.g. Sankri to Juda ka Talab" />
      </div>
      <div className="sm:col-span-2">
        <TextField label="Start" name={`day-${i}-start`} type="number" min={1} max={29000} suffix="ft" value={day.start_ft}
          onChange={(ev) => onChange({ start_ft: ev.target.value })} error={e('start_altitude_m')} />
      </div>
      <div className="sm:col-span-2">
        <TextField label="High point" name={`day-${i}-high`} type="number" min={1} max={29000} suffix="ft" value={day.high_ft}
          onChange={(ev) => onChange({ high_ft: ev.target.value })} error={e('high_altitude_m')} />
      </div>
      <div className="sm:col-span-2">
        <TextField label="End" name={`day-${i}-end`} type="number" min={1} max={29000} suffix="ft" value={day.end_ft}
          onChange={(ev) => onChange({ end_ft: ev.target.value })} error={e('end_altitude_m')} />
      </div>
      <div className="sm:col-span-2">
        <TextField label="Distance" name={`day-${i}-km`} type="number" min={0.1} max={99.9} step={0.1} suffix="km"
          value={day.distance_km} onChange={(ev) => onChange({ distance_km: ev.target.value })} error={e('distance_km')} />
      </div>
      <div className="sm:col-span-1">
        <TextField label="Hours from" name={`day-${i}-hmin`} type="number" min={0.5} max={24} step={0.5} value={day.hours_min}
          onChange={(ev) => onChange({ hours_min: ev.target.value })} error={e('hours_min')} />
      </div>
      <div className="sm:col-span-1">
        <TextField label="to" name={`day-${i}-hmax`} type="number" min={0.5} max={24} step={0.5} value={day.hours_max}
          onChange={(ev) => onChange({ hours_max: ev.target.value })} error={e('hours_max')} />
      </div>
      <div className="sm:col-span-2">
        <TextField label="Route note" name={`day-${i}-note`} maxLength={60} value={day.route_note}
          onChange={(ev) => onChange({ route_note: ev.target.value })} error={e('route_note')} hint="E.g. mostly downhill" />
      </div>
      <div className="sm:col-span-6">
        <TextAreaField label="What the day is like" name={`day-${i}-description`} maxLength={1000} rows={2}
          value={day.description} onChange={(ev) => onChange({ description: ev.target.value })} error={e('description')} />
      </div>
    </div>
  )
}

const MAX_PHOTOS = 30
const NO_WORDS: PhotoWords = { caption: null, place: null, day_number: null }

/** Photos from past runs, shown on the trek page. Uploads save straight away, apart from the track form. */
function TrackPhotos({ track }: { track: Track }) {
  const { withAuth } = useAuth()
  const queryClient = useQueryClient()
  // Read from the list query, not the form's snapshot, so new uploads appear.
  const tracks = useQuery({ queryKey: ['admin-tracks'], queryFn: () => withAuth(listTracks) })
  const photos = tracks.data?.items.find((t) => t.id === track.id)?.photos ?? []
  const [words, setWords] = useState<PhotoWords>(NO_WORDS)
  const [progress, setProgress] = useState<string | null>(null)
  const input = useRef<HTMLInputElement>(null)
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['admin-tracks'] })

  const upload = useMutation({
    mutationFn: async (files: File[]) => {
      for (const [i, file] of files.entries()) {
        setProgress(files.length > 1 ? `Uploading ${i + 1} of ${files.length}…` : 'Uploading…')
        const blob = await shrinkPhoto(file).catch(() => file)
        await withAuth((token) => uploadTrackPhoto(token, track.id, blob, words))
      }
    },
    onSuccess: () => setWords(NO_WORDS),
    onSettled: () => {
      setProgress(null)
      if (input.current) input.current.value = ''
      void refresh()
    },
  })
  const remove = useMutation({
    mutationFn: (photoId: string) => withAuth((token) => deleteTrackPhoto(token, track.id, photoId)),
    onSettled: () => void refresh(),
  })
  const full = photos.length >= MAX_PHOTOS
  const uploadErrors = fieldErrors(upload.error)

  return (
    <Panel title={`Photos (${photos.length} of ${MAX_PHOTOS})`}>
      <p className="text-sm text-stone-600">
        Shown on the trek page in the order you add them; the first one is the cover. Each has a caption, where it
        was taken and which day, e.g. “Summit ridge at first light” · “Kedarkantha summit” · Day 4.
      </p>
      {photos.length > 0 && (
        <ul className="mt-4 grid gap-3 sm:grid-cols-2">
          {photos.map((p) => (
            <PhotoRow key={p.id} photo={p} track={track} onDelete={() => window.confirm('Delete this photo?') && remove.mutate(p.id)} />
          ))}
        </ul>
      )}
      <div className="mt-5 grid gap-3 sm:grid-cols-3">
        <TextField label="Caption" name="photo_caption" maxLength={200} value={words.caption ?? ''}
          onChange={(e) => setWords((w) => ({ ...w, caption: e.target.value }))} error={uploadErrors.caption} />
        <TextField label="Place" name="photo_place" maxLength={100} value={words.place ?? ''}
          onChange={(e) => setWords((w) => ({ ...w, place: e.target.value }))} error={uploadErrors.place} />
        <DaySelect days={track.duration_days} value={words.day_number} onChange={(day_number) => setWords((w) => ({ ...w, day_number }))} name="photo_day" />
      </div>
      <div className="mt-3 flex flex-wrap items-center gap-3">
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
        <span className="text-xs text-stone-500">The words above apply to every photo in this upload; edit each one after.</span>
      </div>
      {full && <p className="mt-3 text-sm text-stone-600">That's the limit. Delete one to add another.</p>}
      {upload.error && Object.keys(uploadErrors).length === 0 && (
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

function PhotoRow({ photo, track, onDelete }: { photo: TrackPhoto; track: Track; onDelete: () => void }) {
  const { withAuth } = useAuth()
  const queryClient = useQueryClient()
  const saved: PhotoWords = { caption: photo.caption, place: photo.place, day_number: photo.day_number }
  const [words, setWords] = useState<PhotoWords>(saved)
  const save = useMutation({
    mutationFn: () => withAuth((token) => describeTrackPhoto(token, track.id, photo.id, words)),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['admin-tracks'] }),
  })
  const errors = fieldErrors(save.error)
  const dirty = JSON.stringify(words) !== JSON.stringify(saved)
  return (
    <li className="overflow-hidden rounded-xl ring-1 ring-stone-200">
      <img src={photo.url} alt={photo.caption ?? ''} loading="lazy" className="aspect-[16/9] w-full object-cover" />
      <div className="grid gap-2 p-3">
        <TextField label="Caption" name={`caption-${photo.id}`} maxLength={200} value={words.caption ?? ''}
          onChange={(e) => setWords((w) => ({ ...w, caption: e.target.value || null }))} error={errors.caption} />
        <div className="grid grid-cols-[1fr_7rem] gap-2">
          <TextField label="Place" name={`place-${photo.id}`} maxLength={100} value={words.place ?? ''}
            onChange={(e) => setWords((w) => ({ ...w, place: e.target.value || null }))} error={errors.place} />
          <DaySelect days={track.duration_days} value={words.day_number} name={`day-${photo.id}`}
            onChange={(day_number) => setWords((w) => ({ ...w, day_number }))} />
        </div>
        <div className="flex items-center justify-between gap-2">
          <Button tone="secondary" disabled={!dirty || save.isPending} onClick={() => save.mutate()}>
            {save.isPending ? 'Saving…' : dirty ? 'Save' : 'Saved'}
          </Button>
          <button type="button" onClick={onDelete} className="text-xs font-medium text-laterite-600 hover:text-laterite-500">
            Delete
          </button>
        </div>
        {save.error && Object.keys(errors).length === 0 && <ErrorNote error={save.error} />}
      </div>
    </li>
  )
}

function DaySelect({ days, value, onChange, name }: { days: number; value: number | null; onChange: (day: number | null) => void; name: string }) {
  return (
    <SelectField label="Day" name={name} placeholder="—" value={value === null ? '' : String(value)}
      onChange={(e) => onChange(e.target.value === '' ? null : Number(e.target.value))}
      options={Array.from({ length: days }, (_, i) => ({ value: String(i + 1), label: `Day ${i + 1}` }))} />
  )
}

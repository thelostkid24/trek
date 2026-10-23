import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { AVATAR_TYPES } from '../../api/account.ts'
import {
  getOwnGuideProfile,
  MAX_SPECIALTIES,
  removeOwnGuideCover,
  removeOwnGuidePhoto,
  updateOwnGuideProfile,
  uploadOwnGuideCover,
  uploadOwnGuidePhoto,
  type Milestone,
  type OwnGuideProfile,
  type RouteItem,
} from '../../api/guide.ts'
import { fieldErrors } from '../../auth/errorMessages.ts'
import { useAuth } from '../../auth/useAuth.ts'
import { Button, ErrorNote, Loading, Panel } from '../../components/admin/AdminUi.tsx'
import { TextField } from '../../components/auth/TextField.tsx'
import { Avatar } from '../../components/Avatar.tsx'
import { RoutePlanFields } from '../../components/guide/RoutePlanFields.tsx'
import { SelectField, TextAreaField } from '../../components/profile/fields.tsx'
import { lastLed, MONTHS, planOf } from '../../lib/guideRecord.ts'
import { shrinkPhoto } from '../../lib/photos.ts'
import { SITE_ORIGIN } from '../../lib/siteLinks.ts'

const QUERY_KEY = ['guide-own-profile']

/**
 * /guide/profile — the guide edits what their public page shows (docs/TRD.md §7.21): photo, cover, name, home,
 * intro, languages, the year they started, bio, specialties, when they can lead each route and a note on it, and
 * career milestones. Photos save as soon as they're picked; everything else saves with one button. Course
 * credentials and the route record (times and last led) stay with the team (§7.12, §7.21.8).
 */
export function GuideProfileEditPage() {
  const { withAuth } = useAuth()
  const profile = useQuery({ queryKey: QUERY_KEY, queryFn: () => withAuth(getOwnGuideProfile) })
  return (
    <div className="mx-auto max-w-3xl space-y-5 px-5 py-6 sm:px-10">
      {profile.isPending ? <Loading /> : profile.isError ? <ErrorNote error={profile.error} /> : <Editor initial={profile.data} />}
    </div>
  )
}

function Editor({ initial }: { initial: OwnGuideProfile }) {
  const auth = useAuth()
  const { withAuth } = auth
  const queryClient = useQueryClient()
  const [form, setForm] = useState({
    full_name: initial.full_name ?? '',
    home_city: initial.home_city ?? '',
    quote: initial.quote ?? '',
    languages: initial.languages ?? '',
    leading_since: initial.leading_since === null ? '' : String(initial.leading_since),
    bio: initial.bio ?? '',
  })
  const [specialties, setSpecialties] = useState(initial.specialty_ids)
  const [milestones, setMilestones] = useState<Milestone[]>(initial.milestones)
  const [routes, setRoutes] = useState<RouteItem[]>(initial.routes)
  const [savedAt, setSavedAt] = useState<number | null>(null)

  const save = useMutation({
    mutationFn: () =>
      withAuth((token) =>
        updateOwnGuideProfile(token, {
          full_name: form.full_name,
          home_city: form.home_city || null,
          quote: form.quote || null,
          languages: form.languages || null,
          leading_since: form.leading_since ? Number(form.leading_since) : null,
          bio: form.bio || null,
          specialty_ids: specialties,
          milestones: milestones.filter((m) => m.text.trim()),
          // A trek without a row is only sent once the guide gives it a plan or a note.
          routes: routes
            .filter((r) => r.id !== null || r.availability !== 'ASK' || r.note?.trim())
            .map((r) => ({ id: r.id, track_id: r.id === null ? r.track_id : null, ...planOf(r) })),
        }),
      ),
    onSuccess: (data) => {
      queryClient.setQueryData(QUERY_KEY, data)
      setRoutes(data.routes)
      void queryClient.invalidateQueries({ queryKey: ['public-guide'] })
      if (auth.status === 'authenticated') auth.updateUser({ ...auth.user, full_name: data.full_name })
      setSavedAt(Date.now())
    },
  })
  const errors = fieldErrors(save.error)
  const setMilestone = (i: number, patch: Partial<Milestone>) =>
    setMilestones(milestones.map((x, j) => (j === i ? { ...x, ...patch } : x)))
  const set = (field: keyof typeof form) => (e: { target: { value: string } }) => setForm((f) => ({ ...f, [field]: e.target.value }))
  const publicUrl = `${SITE_ORIGIN}/guides/${initial.slug}`

  return (
    <>
      <Panel
        title="Your public profile"
        action={
          <Link to={`/guides/${initial.slug}`} className="text-sm font-medium text-brand-800 hover:text-brand-950">
            View it →
          </Link>
        }
      >
        <p className="mt-2 font-mono text-sm break-all text-stone-700">{publicUrl}</p>
        <p className="mt-1 text-sm text-stone-600">Share this link anywhere. Ask the team if you'd like a different address.</p>
      </Panel>

      <Photos initial={initial} />

      <form
        onSubmit={(e) => {
          e.preventDefault()
          save.mutate()
        }}
        className="space-y-5"
      >
        <Panel title="About you">
          <div className="mt-4 grid gap-3 sm:grid-cols-2">
            <TextField label="Name as trekkers see it" name="full_name" required maxLength={100} value={form.full_name} onChange={set('full_name')} error={errors.full_name} />
            <TextField label="Home town" name="home_city" maxLength={100} value={form.home_city} onChange={set('home_city')} error={errors.home_city} hint="E.g. Sankri, Uttarakhand" />
            <div className="sm:col-span-2">
              <TextField
                label="Intro"
                name="quote"
                maxLength={240}
                value={form.quote}
                onChange={set('quote')}
                error={errors.quote}
                hint="One or two lines under your name, e.g. “Guiding from Sankri since 2015. Leads groups of six, never more.”"
              />
            </div>
            <TextField label="Languages" name="languages" maxLength={120} value={form.languages} onChange={set('languages')} error={errors.languages} hint="Separate with commas: Hindi, Garhwali, English" />
            <TextField
              label="Leading treks since"
              name="leading_since"
              type="number"
              inputMode="numeric"
              min={1950}
              max={new Date().getFullYear()}
              value={form.leading_since}
              onChange={set('leading_since')}
              error={errors.leading_since}
              hint="The year you started"
            />
            <div className="sm:col-span-2">
              <TextAreaField
                label="About you, in your words"
                name="bio"
                maxLength={2000}
                rows={8}
                value={form.bio}
                onChange={set('bio')}
                error={errors.bio}
                hint="Where you grew up, how you learned the trails, how you run a group. Leave a blank line between paragraphs."
              />
            </div>
          </div>
        </Panel>

        <Panel title="Specialties">
          <p className="mt-1 text-sm text-stone-600">
            Pick up to {MAX_SPECIALTIES}. {specialties.length} picked.
          </p>
          <div className="mt-3 flex flex-wrap gap-2">
            {initial.specialty_options.map((s) => {
              const on = specialties.includes(s.id)
              return (
                <button
                  key={s.id}
                  type="button"
                  aria-pressed={on}
                  disabled={!on && specialties.length >= MAX_SPECIALTIES}
                  onClick={() => setSpecialties(on ? specialties.filter((id) => id !== s.id) : [...specialties, s.id])}
                  className={`rounded-full px-4 py-1.5 text-sm font-medium transition disabled:opacity-40 ${on ? 'bg-brand-900 text-white' : 'bg-white ring-1 ring-stone-300 hover:ring-brand-400'}`}
                >
                  {s.name}
                </button>
              )
            })}
          </div>
          {errors.specialty_ids && <p className="mt-2 text-sm text-laterite-600">{errors.specialty_ids}</p>}
        </Panel>

        <Panel title="Routes you lead">
          {routes.length === 0 ? (
            <p className="mt-2 text-sm text-stone-600">Your routes show here once you've led one with us or the team has added the ones you led before.</p>
          ) : (
            <div className="mt-3 space-y-5">
              <p className="text-sm text-stone-600">
                When you can lead each route, and a line about it in your words. Times and when you last led it come from the team.
              </p>
              {routes.map((r, i) => (
                <fieldset key={r.id ?? r.track_id} className="space-y-3 border-t border-stone-100 pt-4 first:border-0 first:pt-0">
                  <legend className="flex w-full flex-wrap items-baseline justify-between gap-2">
                    <span className="font-medium text-stone-900">{r.track_name ?? r.name}</span>
                    <span className="font-mono text-xs text-stone-500">
                      {r.times_led !== null ? `${r.times_led}× before` : 'Led'} · last {lastLed(r).toLowerCase()}
                    </span>
                  </legend>
                  <RoutePlanFields
                    plan={r}
                    onChange={(plan) => setRoutes(routes.map((x, j) => (j === i ? { ...x, ...plan } : x)))}
                    name={`route-${i}`}
                    errors={errors}
                    prefix={`routes[${i}]`}
                  />
                </fieldset>
              ))}
            </div>
          )}
        </Panel>

        <Panel
          title="Career in the mountains"
          action={
            <Button
              tone="secondary"
              onClick={() => setMilestones([...milestones, { year: new Date().getFullYear(), month: null, day: null, approximate: false, text: '' }])}
              disabled={milestones.length >= 30}
            >
              + Add milestone
            </Button>
          }
        >
          {milestones.length === 0 ? (
            <p className="mt-2 text-sm text-stone-600">Courses, first seasons, rescues, the 100th trek: the moments that made you the guide you are.</p>
          ) : (
            <ol className="mt-3 space-y-3">
              {milestones.map((m, i) => (
                <li key={i} className="grid gap-2 border-t border-stone-100 pt-3 first:border-0 first:pt-0 sm:grid-cols-[6rem_5rem_4.5rem_1fr_auto] sm:items-start">
                  <TextField
                    label="Year"
                    name={`milestone-year-${i}`}
                    type="number"
                    inputMode="numeric"
                    min={1950}
                    max={new Date().getFullYear()}
                    value={m.year === null ? '' : String(m.year)}
                    onChange={(e) => {
                      const year = e.target.value ? Number(e.target.value) : null
                      setMilestone(i, year === null ? { year, month: null, day: null, approximate: false } : { year })
                    }}
                    error={errors[`milestones[${i}].year`]}
                    hint={m.year === null ? 'Blank shows “—”' : undefined}
                  />
                  <SelectField
                    label="Month"
                    name={`milestone-month-${i}`}
                    placeholder="—"
                    options={MONTHS.map((label, j) => ({ value: String(j + 1), label }))}
                    value={m.month === null ? '' : String(m.month)}
                    disabled={m.year === null}
                    onChange={(e) => {
                      const month = e.target.value ? Number(e.target.value) : null
                      setMilestone(i, month === null ? { month, day: null } : { month, approximate: false })
                    }}
                    error={errors[`milestones[${i}].month`]}
                  />
                  <TextField
                    label="Day"
                    name={`milestone-day-${i}`}
                    type="number"
                    inputMode="numeric"
                    min={1}
                    max={31}
                    disabled={m.month === null}
                    value={m.day === null ? '' : String(m.day)}
                    onChange={(e) => setMilestone(i, { day: e.target.value ? Number(e.target.value) : null })}
                    error={errors[`milestones[${i}].day`]}
                  />
                  <TextField
                    label="What happened"
                    name={`milestone-text-${i}`}
                    maxLength={140}
                    value={m.text}
                    onChange={(e) => setMilestone(i, { text: e.target.value })}
                    error={errors[`milestones[${i}].text`]}
                  />
                  <label className="flex items-center gap-2 text-sm text-stone-700 sm:col-span-4">
                    <input
                      type="checkbox"
                      checked={m.approximate}
                      disabled={m.year === null || m.month !== null}
                      onChange={(e) => setMilestone(i, { approximate: e.target.checked })}
                      className="size-4 rounded border-paper-300"
                    />
                    About that year (shows “~{m.year ?? 'year'}”)
                  </label>
                  <button
                    type="button"
                    onClick={() => setMilestones(milestones.filter((_, j) => j !== i))}
                    className="mt-7 text-sm text-stone-500 hover:text-stone-800"
                    aria-label={`Remove milestone ${i + 1}`}
                  >
                    Remove
                  </button>
                </li>
              ))}
            </ol>
          )}
        </Panel>

        {save.error && Object.keys(errors).length === 0 && <ErrorNote error={save.error} />}
        <div className="flex items-center gap-4">
          <Button type="submit" disabled={save.isPending}>
            {save.isPending ? 'Saving…' : 'Save profile'}
          </Button>
          {savedAt && !save.isPending && !save.error && (
            <p className="text-sm text-pine-700" aria-live="polite">
              Saved.
            </p>
          )}
        </div>
      </form>
    </>
  )
}

/** Photo and cover save as soon as they're picked. */
function Photos({ initial }: { initial: OwnGuideProfile }) {
  const auth = useAuth()
  const { withAuth } = auth
  const queryClient = useQueryClient()
  const saved = (data: OwnGuideProfile) => {
    queryClient.setQueryData(QUERY_KEY, data)
    void queryClient.invalidateQueries({ queryKey: ['public-guide'] })
    if (auth.status === 'authenticated') auth.updateUser({ ...auth.user, avatar_url: data.avatar_url })
  }
  const photo = useMutation({
    mutationFn: async (file: File | null) => {
      if (!file) return withAuth(removeOwnGuidePhoto)
      const jpeg = await shrinkPhoto(file)
      return withAuth((token) => uploadOwnGuidePhoto(token, jpeg))
    },
    onSuccess: saved,
  })
  const cover = useMutation({
    mutationFn: async (file: File | null) => {
      if (!file) return withAuth(removeOwnGuideCover)
      const jpeg = await shrinkPhoto(file)
      return withAuth((token) => uploadOwnGuideCover(token, jpeg))
    },
    onSuccess: saved,
  })
  const current = queryClient.getQueryData<OwnGuideProfile>(QUERY_KEY) ?? initial

  return (
    <Panel title="Photo and cover">
      <div className="mt-4 overflow-hidden rounded-xl bg-paper-200">
        {current.cover_url ? (
          <img src={current.cover_url} alt="Your cover" className="aspect-[3/1] w-full object-cover" />
        ) : (
          <div className="flex aspect-[3/1] items-center justify-center text-sm text-stone-500">No cover photo yet</div>
        )}
      </div>
      <div className="mt-3 flex flex-wrap items-center gap-4">
        <PickPhoto label={cover.isPending ? 'Uploading…' : current.cover_url ? 'Change cover' : 'Add cover photo'} disabled={cover.isPending} onPick={(f) => cover.mutate(f)} />
        {current.cover_url && (
          <button type="button" onClick={() => cover.mutate(null)} disabled={cover.isPending} className="text-sm text-stone-500 hover:text-stone-800">
            Remove cover
          </button>
        )}
        <span className="text-xs text-stone-500">Wide landscape shots work best; we crop to 3:1.</span>
      </div>
      <div className="mt-5 flex flex-wrap items-center gap-4">
        <Avatar url={current.avatar_url} name={current.full_name} size="md" />
        <PickPhoto label={photo.isPending ? 'Uploading…' : current.avatar_url ? 'Change photo' : 'Upload photo'} disabled={photo.isPending} onPick={(f) => photo.mutate(f)} />
        {current.avatar_url && (
          <button type="button" onClick={() => photo.mutate(null)} disabled={photo.isPending} className="text-sm text-stone-500 hover:text-stone-800">
            Remove
          </button>
        )}
      </div>
      {(photo.error || cover.error) && (
        <div className="mt-3">
          <ErrorNote error={photo.error ?? cover.error} />
        </div>
      )}
    </Panel>
  )
}

function PickPhoto({ label, disabled, onPick }: { label: string; disabled: boolean; onPick: (file: File) => void }) {
  return (
    <label className="cursor-pointer text-sm font-medium text-brand-800 hover:text-brand-950">
      {label}
      <input
        type="file"
        accept={AVATAR_TYPES.join(',')}
        className="sr-only"
        disabled={disabled}
        onChange={(e) => {
          const file = e.target.files?.[0]
          e.target.value = ''
          if (file) onPick(file)
        }}
      />
    </label>
  )
}

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import {
  getGuideDetails,
  getGuideProfile,
  listGuides,
  listTracks,
  promoteGuide,
  removeGuidePhoto,
  updateGuideDetails,
  updateGuideProfile,
  uploadGuidePhoto,
  type GuideDetailsInput,
  type GuideProfile,
} from '../../api/admin.ts'
import { AVATAR_TYPES } from '../../api/account.ts'
import { fieldErrors } from '../../auth/errorMessages.ts'
import { useAuth } from '../../auth/useAuth.ts'
import { ErrorNote, Loading, Panel, Button } from '../../components/admin/AdminUi.tsx'
import { TextField } from '../../components/auth/TextField.tsx'
import { Avatar } from '../../components/Avatar.tsx'
import { SelectField, TextAreaField } from '../../components/profile/fields.tsx'
import { shrinkPhoto } from '../../lib/photos.ts'

export function GuidesAdminPage() {
  const { withAuth } = useAuth()
  const queryClient = useQueryClient()
  const guides = useQuery({ queryKey: ['admin-guides'], queryFn: () => withAuth(listGuides) })
  const [email, setEmail] = useState('')
  /** Which guide's panel is open, and which: their profile or their credentials. */
  const [open, setOpen] = useState<{ id: string; panel: 'profile' | 'credentials' } | null>(null)
  const promote = useMutation({
    mutationFn: (value: string) => withAuth((token) => promoteGuide(token, value)),
    onSuccess: () => {
      setEmail('')
      void queryClient.invalidateQueries({ queryKey: ['admin-guides'] })
    },
  })

  const submit = (e: FormEvent) => {
    e.preventDefault()
    promote.mutate(email.trim())
  }

  return (
    <>
      <Panel title="Add a guide">
        <form onSubmit={submit} className="flex flex-col gap-3 sm:flex-row sm:items-end">
          <div className="flex-1">
            <TextField
              label="Email of an existing account"
              name="email"
              type="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              error={fieldErrors(promote.error).email}
              hint="They sign up as a trekker first. The guide role shows after their next sign-in."
            />
          </div>
          <Button type="submit" disabled={promote.isPending} className="sm:mb-6">
            {promote.isPending ? 'Adding…' : 'Make guide'}
          </Button>
        </form>
        {promote.error && !fieldErrors(promote.error).email && (
          <div className="mt-3">
            <ErrorNote error={promote.error} />
          </div>
        )}
      </Panel>

      <Panel title="Guides">
        {guides.isPending ? (
          <Loading />
        ) : guides.isError ? (
          <ErrorNote error={guides.error} />
        ) : guides.data.items.length === 0 ? (
          <p className="text-sm text-stone-600">No guides yet.</p>
        ) : (
          <ul className="divide-y divide-stone-100">
            {guides.data.items.map((g) => (
              <li key={g.id} className="py-3">
                <div className="flex items-center gap-3">
                  <Avatar url={g.avatar_url} name={g.full_name} />
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-medium">{g.full_name ?? 'No name yet'}</p>
                    <p className="truncate text-sm text-stone-500">{[g.email, g.phone].filter(Boolean).join(' · ')}</p>
                  </div>
                  {(['profile', 'credentials'] as const).map((panel) => {
                    const on = open?.id === g.id && open.panel === panel
                    return (
                      <Button key={panel} tone="secondary" onClick={() => setOpen(on ? null : { id: g.id, panel })}>
                        {on ? 'Close' : panel === 'profile' ? 'Profile' : 'Credentials'}
                      </Button>
                    )
                  })}
                </div>
                {open?.id === g.id && open.panel === 'profile' && <ProfileForm guideId={g.id} onDone={() => setOpen(null)} />}
                {open?.id === g.id && open.panel === 'credentials' && <DetailsForm guideId={g.id} onDone={() => setOpen(null)} />}
              </li>
            ))}
          </ul>
        )}
      </Panel>
    </>
  )
}

/**
 * The guide's name, photo, home town and bio as the trek, departure and guide pages show them (docs/TRD.md §7.12).
 * A guide has no profile screen of their own, so an admin sets these.
 */
function ProfileForm({ guideId, onDone }: { guideId: string; onDone: () => void }) {
  const { withAuth } = useAuth()
  const profile = useQuery({
    queryKey: ['admin-guide-profile', guideId],
    queryFn: () => withAuth((token) => getGuideProfile(token, guideId)),
  })
  if (profile.isPending) return <Loading />
  if (profile.isError) return <ErrorNote error={profile.error} />
  return <ProfileFields guideId={guideId} initial={profile.data} onDone={onDone} />
}

function ProfileFields({ guideId, initial, onDone }: { guideId: string; initial: GuideProfile; onDone: () => void }) {
  const { withAuth } = useAuth()
  const queryClient = useQueryClient()
  const [form, setForm] = useState({ full_name: initial.full_name ?? '', home_city: initial.home_city ?? '', bio: initial.bio ?? '' })
  const [photo, setPhoto] = useState(initial.avatar_url)
  const saved = (data: GuideProfile) => {
    queryClient.setQueryData(['admin-guide-profile', guideId], data)
    void queryClient.invalidateQueries({ queryKey: ['admin-guides'] })
  }
  const save = useMutation({
    mutationFn: () =>
      withAuth((token) => updateGuideProfile(token, guideId, { full_name: form.full_name, home_city: form.home_city || null, bio: form.bio || null })),
    onSuccess: (data) => {
      saved(data)
      onDone()
    },
  })
  const upload = useMutation({
    mutationFn: async (file: File) => {
      const jpeg = await shrinkPhoto(file)
      return withAuth((token) => uploadGuidePhoto(token, guideId, jpeg))
    },
    onSuccess: (data) => {
      setPhoto(data.avatar_url)
      saved(data)
    },
  })
  const remove = useMutation({
    mutationFn: () => withAuth((token) => removeGuidePhoto(token, guideId)),
    onSuccess: (data) => {
      setPhoto(null)
      saved(data)
    },
  })
  const errors = fieldErrors(save.error)

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault()
        save.mutate()
      }}
      className="mt-3 grid gap-3 rounded-xl border border-stone-200 p-4 sm:grid-cols-2"
    >
      <div className="flex items-center gap-4 sm:col-span-2">
        <Avatar url={photo} name={form.full_name || null} size="md" />
        <label className="cursor-pointer text-sm font-medium text-brand-800 hover:text-brand-950">
          {upload.isPending ? 'Uploading…' : photo ? 'Change photo' : 'Upload photo'}
          <input
            type="file"
            accept={AVATAR_TYPES.join(',')}
            className="sr-only"
            disabled={upload.isPending}
            onChange={(e) => {
              const file = e.target.files?.[0]
              e.target.value = ''
              if (file) upload.mutate(file)
            }}
          />
        </label>
        {photo && (
          <button type="button" onClick={() => remove.mutate()} disabled={remove.isPending} className="text-sm text-stone-500 hover:text-stone-800">
            Remove
          </button>
        )}
      </div>
      {(upload.error || remove.error) && (
        <div className="sm:col-span-2">
          <ErrorNote error={upload.error ?? remove.error} />
        </div>
      )}
      <TextField label="Name as trekkers see it" name="full_name" required maxLength={100} value={form.full_name}
        onChange={(e) => setForm((f) => ({ ...f, full_name: e.target.value }))} error={errors.full_name} />
      <TextField label="Home town" name="home_city" maxLength={100} value={form.home_city}
        onChange={(e) => setForm((f) => ({ ...f, home_city: e.target.value }))} error={errors.home_city} hint="E.g. Chakrata, Dehradun" />
      <div className="sm:col-span-2">
        <TextAreaField label="Bio" name="bio" maxLength={2000} rows={8} value={form.bio}
          onChange={(e) => setForm((f) => ({ ...f, bio: e.target.value }))} error={errors.bio}
          hint="A few short paragraphs; leave a blank line between them. The first shows, the rest open with “Read more”." />
      </div>
      {save.error && Object.keys(errors).length === 0 && (
        <div className="sm:col-span-2">
          <ErrorNote error={save.error} />
        </div>
      )}
      <div className="sm:col-span-2">
        <Button type="submit" disabled={save.isPending}>
          {save.isPending ? 'Saving…' : 'Save profile'}
        </Button>
      </div>
    </form>
  )
}

/**
 * What trekkers read about a guide on the trek, departure and guide pages (docs/TRD.md §7.12). Years leading is
 * worked out from the year they started.
 */
function DetailsForm({ guideId, onDone }: { guideId: string; onDone: () => void }) {
  const { withAuth } = useAuth()
  const details = useQuery({
    queryKey: ['admin-guide-details', guideId],
    queryFn: () => withAuth((token) => getGuideDetails(token, guideId)),
  })
  if (details.isPending) return <Loading />
  if (details.isError) return <ErrorNote error={details.error} />
  const { guide_id: _g, years_leading: _y, ...input } = details.data
  return <DetailsFields guideId={guideId} initial={input} onDone={onDone} />
}

/** IMF-recognised mountaineering institutes, offered as suggestions for the BMC and AMC fields. */
const INSTITUTES = [
  'Nehru Institute of Mountaineering (NIM), Uttarkashi',
  'Atal Bihari Vajpayee Institute of Mountaineering and Allied Sports (ABVIMAS), Manali',
  'Himalayan Mountaineering Institute (HMI), Darjeeling',
  'Jawahar Institute of Mountaineering and Winter Sports (JIM&WS), Pahalgam',
]

function DetailsFields({ guideId, initial, onDone }: { guideId: string; initial: GuideDetailsInput; onDone: () => void }) {
  const { withAuth } = useAuth()
  const queryClient = useQueryClient()
  const [form, setForm] = useState(initial)
  const save = useMutation({
    mutationFn: () => withAuth((token) => updateGuideDetails(token, guideId, form)),
    onSuccess: (data) => {
      queryClient.setQueryData(['admin-guide-details', guideId], data)
      onDone()
    },
  })
  const errors = fieldErrors(save.error)
  const text = (key: keyof GuideDetailsInput) => (e: { target: { value: string } }) =>
    setForm((f) => ({ ...f, [key]: e.target.value || null }))

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault()
        save.mutate()
      }}
      className="mt-3 grid gap-3 rounded-xl border border-stone-200 p-4 sm:grid-cols-2"
    >
      <TextField label="Leading treks since" name="leading_since" type="number" min={1950} max={new Date().getFullYear()}
        value={form.leading_since ?? ''} onChange={(e) => setForm((f) => ({ ...f, leading_since: e.target.value ? Number(e.target.value) : null }))}
        error={errors.leading_since} hint="Year. The page shows “6 years leading”." />
      <TextField label="Languages" name="languages" maxLength={120} value={form.languages ?? ''} onChange={text('languages')}
        error={errors.languages} hint="E.g. Hindi, Garhwali, English" />
      <datalist id="mountaineering-institutes">
        {INSTITUTES.map((i) => (
          <option key={i} value={i}>
            {i}
          </option>
        ))}
      </datalist>
      <TextField label="BMC institute" name="bmc_institute" maxLength={160} list="mountaineering-institutes"
        value={form.bmc_institute ?? ''} onChange={text('bmc_institute')} error={errors.bmc_institute}
        hint="Basic Mountaineering Course. Pick or type the institute." />
      <TextField label="BMC certificate number" name="bmc_certificate_number" maxLength={60}
        value={form.bmc_certificate_number ?? ''} onChange={text('bmc_certificate_number')} error={errors.bmc_certificate_number} />
      <TextField label="AMC institute" name="amc_institute" maxLength={160} list="mountaineering-institutes"
        value={form.amc_institute ?? ''} onChange={text('amc_institute')} error={errors.amc_institute}
        hint="Advanced Mountaineering Course." />
      <TextField label="AMC certificate number" name="amc_certificate_number" maxLength={60}
        value={form.amc_certificate_number ?? ''} onChange={text('amc_certificate_number')} error={errors.amc_certificate_number} />
      <TextField label="Other certification" name="certification" maxLength={160} value={form.certification ?? ''}
        onChange={text('certification')} error={errors.certification} hint="Optional. E.g. Wilderness First Responder" />
      <TextField label="Other certificate number" name="certification_number" maxLength={60} value={form.certification_number ?? ''}
        onChange={text('certification_number')} error={errors.certification_number} />
      <div className="sm:col-span-2">
        <TextAreaField label="In their own words" name="quote" maxLength={240} rows={2} value={form.quote ?? ''}
          onChange={text('quote')} error={errors.quote} hint="One or two lines, shown in quotes." />
      </div>
      <PriorTreks value={form.prior_treks} onChange={(prior_treks) => setForm((f) => ({ ...f, prior_treks }))}
        error={errors.prior_treks} />
      {save.error && Object.keys(errors).length === 0 && (
        <div className="sm:col-span-2">
          <ErrorNote error={save.error} />
        </div>
      )}
      <div className="sm:col-span-2">
        <Button type="submit" disabled={save.isPending}>
          {save.isPending ? 'Saving…' : 'Save credentials'}
        </Button>
      </div>
    </form>
  )
}

type PriorTrekRow = GuideDetailsInput['prior_treks'][number]

/**
 * How often the guide led each trek before Sahyātri. The trek, departure and guide pages add the departures they've
 * completed with us, so a guide new to the site doesn't read "First time leading it".
 */
function PriorTreks({ value, onChange, error }: { value: PriorTrekRow[]; onChange: (rows: PriorTrekRow[]) => void; error?: string }) {
  const { withAuth } = useAuth()
  const tracks = useQuery({ queryKey: ['admin-tracks'], queryFn: () => withAuth(listTracks) })
  const set = (i: number, row: Partial<PriorTrekRow>) => onChange(value.map((r, j) => (j === i ? { ...r, ...row } : r)))
  const options = (tracks.data?.items ?? []).map((t) => ({ value: t.id, label: t.name }))

  return (
    <fieldset className="sm:col-span-2">
      <legend className="text-sm font-medium text-stone-800">Treks led before Sahyātri</legend>
      <p className="mt-0.5 text-sm text-stone-500">
        Times led elsewhere. The pages add the departures they complete with us.
      </p>
      {tracks.isError && <ErrorNote error={tracks.error} />}
      <ul className="mt-2 space-y-2">
        {value.map((row, i) => (
          <li key={i} className="grid grid-cols-[1fr_6rem_auto] items-end gap-2">
            <SelectField label="Trek" name={`prior_trek_${i}`} required options={options} value={row.track_id}
              onChange={(e) => set(i, { track_id: e.target.value })} />
            <TextField label="Times" name={`prior_times_${i}`} type="number" required min={1} max={1000} step="1"
              value={row.times || ''} onChange={(e) => set(i, { times: Number(e.target.value) })} />
            <button type="button" onClick={() => onChange(value.filter((_, j) => j !== i))}
              className="mb-2.5 text-sm text-stone-500 hover:text-stone-800">
              Remove
            </button>
          </li>
        ))}
      </ul>
      <button type="button" onClick={() => onChange([...value, { track_id: '', times: 0 }])}
        className="mt-2 text-sm font-semibold text-brand-800 hover:text-brand-900">
        + Add a trek
      </button>
      {error && <p className="mt-1 text-sm text-laterite-600">{error}</p>}
    </fieldset>
  )
}

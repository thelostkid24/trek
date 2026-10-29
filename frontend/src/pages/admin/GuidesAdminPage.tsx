import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import {
  changeGuideSlug,
  createGuideSpecialty,
  deleteGuideGalleryItem,
  deleteGuideSpecialty,
  getGuideDetails,
  getGuideGallery,
  getGuideProfile,
  listGuides,
  listGuideSpecialties,
  promoteGuide,
  removeGuideCover,
  removeGuidePhoto,
  updateGuideDetails,
  updateGuideProfile,
  uploadGuideCover,
  uploadGuideGalleryPhoto,
  uploadGuidePhoto,
  type GuideDetailsInput,
  type GuideProfile,
} from '../../api/admin.ts'
import { AVATAR_TYPES } from '../../api/account.ts'
import { MAX_MEDIA } from '../../api/guideMedia.ts'
import { fieldErrors } from '../../auth/errorMessages.ts'
import { useAuth } from '../../auth/useAuth.ts'
import { ErrorNote, Loading, Panel, Button } from '../../components/admin/AdminUi.tsx'
import { GuideCertificatesPanel } from '../../components/admin/GuideCertificatesPanel.tsx'
import { GuideRecordPanel } from '../../components/admin/GuideRecordPanel.tsx'
import { TextField } from '../../components/auth/TextField.tsx'
import { Avatar } from '../../components/Avatar.tsx'
import { TextAreaField } from '../../components/profile/fields.tsx'
import { shrinkPhoto } from '../../lib/photos.ts'
import { SITE_ORIGIN } from '../../lib/siteLinks.ts'

type PanelKind = 'profile' | 'credentials' | 'certificates' | 'record' | 'gallery'

const PANELS: { panel: PanelKind; label: string }[] = [
  { panel: 'profile', label: 'Profile' },
  { panel: 'credentials', label: 'Credentials' },
  { panel: 'certificates', label: 'Certificates' },
  { panel: 'record', label: 'Record' },
  { panel: 'gallery', label: 'Gallery' },
]

export function GuidesAdminPage() {
  const { withAuth } = useAuth()
  const queryClient = useQueryClient()
  const guides = useQuery({ queryKey: ['admin-guides'], queryFn: () => withAuth(listGuides) })
  const [email, setEmail] = useState('')
  /** Which guide's panel is open, and which. */
  const [open, setOpen] = useState<{ id: string; panel: PanelKind } | null>(null)
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
                  <div className="flex flex-wrap justify-end gap-2">
                    {PANELS.map(({ panel, label }) => {
                      const on = open?.id === g.id && open.panel === panel
                      return (
                        <Button key={panel} tone="secondary" onClick={() => setOpen(on ? null : { id: g.id, panel })}>
                          {on ? 'Close' : label}
                        </Button>
                      )
                    })}
                  </div>
                </div>
                {open?.id === g.id && open.panel === 'profile' && <ProfileForm guideId={g.id} onDone={() => setOpen(null)} />}
                {open?.id === g.id && open.panel === 'credentials' && <DetailsForm guideId={g.id} onDone={() => setOpen(null)} />}
                {open?.id === g.id && open.panel === 'certificates' && <GuideCertificatesPanel guideId={g.id} />}
                {open?.id === g.id && open.panel === 'record' && <GuideRecordPanel guideId={g.id} onDone={() => setOpen(null)} />}
                {open?.id === g.id && open.panel === 'gallery' && <GalleryPanel guideId={g.id} />}
              </li>
            ))}
          </ul>
        )}
      </Panel>

      <SpecialtiesPanel />
    </>
  )
}

/** The specialties guides pick up to eight of on their profile (docs/TRD.md §7.21). Removing one drops it everywhere. */
function SpecialtiesPanel() {
  const { withAuth } = useAuth()
  const queryClient = useQueryClient()
  const list = useQuery({ queryKey: ['admin-guide-specialties'], queryFn: () => withAuth(listGuideSpecialties) })
  const [name, setName] = useState('')
  const refresh = () => void queryClient.invalidateQueries({ queryKey: ['admin-guide-specialties'] })
  const add = useMutation({
    mutationFn: () => withAuth((token) => createGuideSpecialty(token, name.trim())),
    onSuccess: () => {
      setName('')
      refresh()
    },
  })
  const remove = useMutation({
    mutationFn: (id: string) => withAuth((token) => deleteGuideSpecialty(token, id)),
    onSuccess: refresh,
  })

  return (
    <Panel title="Guide specialties">
      {list.isPending ? (
        <Loading />
      ) : list.isError ? (
        <ErrorNote error={list.error} />
      ) : (
        <ul className="mt-3 flex flex-wrap gap-2">
          {list.data.items.map((s) => (
            <li key={s.id} className="flex items-center gap-2 rounded-full bg-white py-1 pr-2 pl-4 text-sm ring-1 ring-stone-300">
              {s.name}
              <button
                type="button"
                aria-label={`Remove ${s.name}`}
                disabled={remove.isPending}
                onClick={() => {
                  if (window.confirm(`Remove “${s.name}”? Guides who picked it lose it.`)) remove.mutate(s.id)
                }}
                className="rounded-full px-1.5 text-stone-500 hover:text-stone-900"
              >
                ✕
              </button>
            </li>
          ))}
        </ul>
      )}
      <form
        onSubmit={(e) => {
          e.preventDefault()
          add.mutate()
        }}
        className="mt-4 flex flex-col gap-3 sm:flex-row sm:items-end"
      >
        <div className="flex-1">
          <TextField label="New specialty" name="specialty" required maxLength={40} value={name} onChange={(e) => setName(e.target.value)} error={fieldErrors(add.error).name} />
        </div>
        <Button type="submit" disabled={add.isPending} className="sm:mb-6">
          {add.isPending ? 'Adding…' : 'Add'}
        </Button>
      </form>
      {(add.error || remove.error) && !fieldErrors(add.error).name && <ErrorNote error={add.error ?? remove.error} />}
    </Panel>
  )
}

/**
 * The photos on the guide's page (§7.21.4), the same list the guide edits from their own page. Several files can be
 * picked at once; each is shrunk and uploaded in turn. Videos, captions and order stay with the guide.
 */
function GalleryPanel({ guideId }: { guideId: string }) {
  const { withAuth } = useAuth()
  const queryClient = useQueryClient()
  const key = ['admin-guide-gallery', guideId]
  const items = useQuery({ queryKey: key, queryFn: () => withAuth((token) => getGuideGallery(token, guideId)) })
  const refresh = () => void queryClient.invalidateQueries({ queryKey: key })
  const upload = useMutation({
    mutationFn: async (files: File[]) => {
      for (const file of files) {
        const jpeg = await shrinkPhoto(file)
        await withAuth((token) => uploadGuideGalleryPhoto(token, guideId, jpeg))
      }
    },
    onSettled: refresh,
  })
  const remove = useMutation({
    mutationFn: (id: string) => withAuth((token) => deleteGuideGalleryItem(token, guideId, id)),
    onSuccess: refresh,
  })

  if (items.isPending) return <Loading />
  if (items.isError) return <ErrorNote error={items.error} />
  const room = MAX_MEDIA - items.data.length
  return (
    <div className="mt-3 rounded-xl border border-stone-200 p-4">
      {items.data.length === 0 ? (
        <p className="text-sm text-stone-600">No photos or videos yet.</p>
      ) : (
        <ul className="grid grid-cols-3 gap-2 sm:grid-cols-5">
          {items.data.map((m) => (
            <li key={m.id} className="relative">
              <img src={m.url ?? m.thumbnail_url ?? ''} alt={m.caption ?? ''} className="aspect-square w-full rounded-lg object-cover" />
              <button
                type="button"
                aria-label="Remove this photo"
                disabled={remove.isPending}
                onClick={() => {
                  if (window.confirm('Remove this from their page?')) remove.mutate(m.id)
                }}
                className="absolute top-1 right-1 rounded-full bg-black/60 px-2 text-sm text-white hover:bg-black/80"
              >
                ✕
              </button>
            </li>
          ))}
        </ul>
      )}
      <div className="mt-3 flex flex-wrap items-center gap-4">
        <label className={`text-sm font-medium ${room > 0 ? 'cursor-pointer text-brand-800 hover:text-brand-950' : 'text-stone-400'}`}>
          {upload.isPending ? 'Uploading…' : 'Add photos'}
          <input
            type="file"
            multiple
            accept={AVATAR_TYPES.join(',')}
            className="sr-only"
            disabled={upload.isPending || room <= 0}
            onChange={(e) => {
              const files = [...(e.target.files ?? [])].slice(0, room)
              e.target.value = ''
              if (files.length > 0) upload.mutate(files)
            }}
          />
        </label>
        <span className="text-xs text-stone-500">{room} of {MAX_MEDIA} places left. Shown in “Photos” on their page.</span>
      </div>
      {(upload.error || remove.error) && (
        <div className="mt-3">
          <ErrorNote error={upload.error ?? remove.error} />
        </div>
      )}
    </div>
  )
}

/**
 * The guide's name, photo, cover, home town and bio as the trek, departure and guide pages show them (docs/TRD.md
 * §7.12), and their profile address (§7.21). The guide edits the same fields from /guide/profile.
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

/**
 * The "How was your trek?" link (/r/{slug}, docs/TRD.md §7.21.5) to send the guide, so they can share it with people
 * they trekked with. The same link they see under Reviews in their console.
 */
function ReviewLink({ slug }: { slug: string }) {
  const [copied, setCopied] = useState(false)
  const link = `${SITE_ORIGIN}/r/${slug}`
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(link)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      setCopied(false)
    }
  }
  return (
    <div className="flex flex-wrap items-center gap-x-3 gap-y-1 rounded-lg bg-stone-50 px-3 py-2.5 text-sm sm:col-span-2">
      <span className="font-medium text-stone-800">Review link</span>
      <a href={link} target="_blank" rel="noreferrer" className="min-w-0 break-all text-brand-800 hover:text-brand-950">
        {link}
      </a>
      <button type="button" onClick={() => void copy()} className="font-medium text-brand-800 hover:text-brand-950">
        {copied ? 'Copied' : 'Copy'}
      </button>
      <span className="w-full text-xs text-stone-500">Send it to the guide to share with people they trekked with.</span>
    </div>
  )
}

function ProfileFields({ guideId, initial, onDone }: { guideId: string; initial: GuideProfile; onDone: () => void }) {
  const { withAuth } = useAuth()
  const queryClient = useQueryClient()
  const [form, setForm] = useState({ full_name: initial.full_name ?? '', home_city: initial.home_city ?? '', bio: initial.bio ?? '' })
  const [photo, setPhoto] = useState(initial.avatar_url)
  const [cover, setCover] = useState(initial.cover_url)
  const [slug, setSlug] = useState(initial.slug)
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
  const uploadCover = useMutation({
    mutationFn: async (file: File) => {
      const jpeg = await shrinkPhoto(file)
      return withAuth((token) => uploadGuideCover(token, guideId, jpeg))
    },
    onSuccess: (data) => {
      setCover(data.cover_url)
      saved(data)
    },
  })
  const removeCover = useMutation({
    mutationFn: () => withAuth((token) => removeGuideCover(token, guideId)),
    onSuccess: (data) => {
      setCover(null)
      saved(data)
    },
  })
  const moveTo = useMutation({
    mutationFn: () => withAuth((token) => changeGuideSlug(token, guideId, slug.trim())),
    onSuccess: (data) => {
      setSlug(data.slug)
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
      <div className="sm:col-span-2">
        <div className="overflow-hidden rounded-lg bg-stone-100">
          {cover ? (
            <img src={cover} alt="Their cover" className="aspect-[3/1] w-full object-cover" />
          ) : (
            <div className="flex aspect-[3/1] items-center justify-center text-sm text-stone-500">No cover photo yet</div>
          )}
        </div>
        <div className="mt-2 flex flex-wrap items-center gap-4">
          <label className="cursor-pointer text-sm font-medium text-brand-800 hover:text-brand-950">
            {uploadCover.isPending ? 'Uploading…' : cover ? 'Change cover' : 'Add cover photo'}
            <input
              type="file"
              accept={AVATAR_TYPES.join(',')}
              className="sr-only"
              disabled={uploadCover.isPending}
              onChange={(e) => {
                const file = e.target.files?.[0]
                e.target.value = ''
                if (file) uploadCover.mutate(file)
              }}
            />
          </label>
          {cover && (
            <button type="button" onClick={() => removeCover.mutate()} disabled={removeCover.isPending} className="text-sm text-stone-500 hover:text-stone-800">
              Remove cover
            </button>
          )}
          <span className="text-xs text-stone-500">The banner on their page. Wide landscape shots work best; we crop to 3:1.</span>
        </div>
      </div>
      {(uploadCover.error || removeCover.error) && (
        <div className="sm:col-span-2">
          <ErrorNote error={uploadCover.error ?? removeCover.error} />
        </div>
      )}
      <div className="flex flex-col gap-2 sm:col-span-2 sm:flex-row sm:items-end">
        <div className="flex-1">
          <TextField label="Profile address" name="slug" prefix="theemptyvalley.com/guides/" maxLength={60} value={slug}
            onChange={(e) => setSlug(e.target.value)} error={fieldErrors(moveTo.error).slug}
            hint="Lower-case letters, digits and hyphens. The old address keeps working." />
        </div>
        <Button tone="secondary" disabled={moveTo.isPending || slug.trim() === initial.slug} onClick={() => moveTo.mutate()} className="sm:mb-6">
          {moveTo.isPending ? 'Changing…' : 'Change address'}
        </Button>
      </div>
      {moveTo.error && !fieldErrors(moveTo.error).slug && (
        <div className="sm:col-span-2">
          <ErrorNote error={moveTo.error} />
        </div>
      )}
      <ReviewLink slug={initial.slug} />
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
  const { guide_id: _g, years_leading: _y, prior_treks: _p, ...input } = details.data
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
      <TextField label="Batches led" name="batches_led" type="number" min={0} max={10000}
        value={form.batches_led ?? ''} onChange={(e) => setForm((f) => ({ ...f, batches_led: e.target.value ? Number(e.target.value) : null }))}
        error={errors.batches_led} hint="Shown on Our Guides and their page. Leave empty to count it from their routes." />
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
      <label className="flex items-start gap-2 text-sm text-stone-700 sm:col-span-2">
        <input
          type="checkbox"
          name="route_verified"
          checked={form.route_verified}
          onChange={(e) => setForm((f) => ({ ...f, route_verified: e.target.checked }))}
          className="mt-0.5 size-4 rounded border-stone-300"
        />
        <span>
          Route verified
          <span className="block text-stone-500">Shows the tag on Our Guides. Guides who’ve led more than 7 treks get it anyway.</span>
        </span>
      </label>
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

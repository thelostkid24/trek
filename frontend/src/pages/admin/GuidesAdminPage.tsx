import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import {
  getGuideDetails,
  listGuides,
  promoteGuide,
  updateGuideDetails,
  type GuideDetailsInput,
} from '../../api/admin.ts'
import { fieldErrors } from '../../auth/errorMessages.ts'
import { useAuth } from '../../auth/useAuth.ts'
import { ErrorNote, Loading, Panel, Button } from '../../components/admin/AdminUi.tsx'
import { TextField } from '../../components/auth/TextField.tsx'
import { Avatar } from '../../components/Avatar.tsx'
import { TextAreaField } from '../../components/profile/fields.tsx'

export function GuidesAdminPage() {
  const { withAuth } = useAuth()
  const queryClient = useQueryClient()
  const guides = useQuery({ queryKey: ['admin-guides'], queryFn: () => withAuth(listGuides) })
  const [email, setEmail] = useState('')
  const [open, setOpen] = useState<string | null>(null)
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
                  <Button tone="secondary" onClick={() => setOpen(open === g.id ? null : g.id)}>
                    {open === g.id ? 'Close' : 'Credentials'}
                  </Button>
                </div>
                {open === g.id && <DetailsForm guideId={g.id} onDone={() => setOpen(null)} />}
              </li>
            ))}
          </ul>
        )}
      </Panel>
    </>
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
      <TextField label="Certification" name="certification" maxLength={160} value={form.certification ?? ''}
        onChange={text('certification')} error={errors.certification} hint="E.g. NIM Basic Mountaineering Course" />
      <TextField label="Certificate number" name="certification_number" maxLength={60} value={form.certification_number ?? ''}
        onChange={text('certification_number')} error={errors.certification_number} />
      <div className="sm:col-span-2">
        <TextAreaField label="In their own words" name="quote" maxLength={240} rows={2} value={form.quote ?? ''}
          onChange={text('quote')} error={errors.quote} hint="One or two lines, shown in quotes." />
      </div>
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

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import {
  CERTIFICATION_LABELS,
  CERTIFICATION_TYPES,
  certificationTitle,
  createCertification,
  credentialsKey,
  deleteCertification,
  DOCUMENT_TYPES,
  getGuideCredentials,
  listOwnCertifications,
  MAX_DOCUMENT_BYTES,
  openCertificationDocument,
  updateCertification,
  uploadCertificationDocument,
  type Certification,
  type CertificationInput,
  type CertificationStatus,
  type CertificationType,
  type PublicCertification,
} from '../../../api/certifications.ts'
import { fieldErrors } from '../../../auth/errorMessages.ts'
import { useAuth } from '../../../auth/useAuth.ts'
import { TextField } from '../../auth/TextField.tsx'
import { SelectField } from '../../profile/fields.tsx'
import { ShieldIcon } from './TierBadge.tsx'
import type { GuideSectionProps } from './types.ts'

const OWN_KEY = ['own-certifications'] as const

/**
 * "Certifications and safety" (§7.21.3). Visitors see verified certificates only (expired ones marked so); the owner
 * sees all of theirs, pending ones too, and adds or edits them in place. An admin checks each against its document.
 */
export function CertificationsSection({ guide, owner }: GuideSectionProps) {
  const { withAuth } = useAuth()
  const visitor = useQuery({
    queryKey: credentialsKey(guide.id),
    queryFn: () => getGuideCredentials(guide.id),
    enabled: !owner,
  })
  const own = useQuery({ queryKey: OWN_KEY, queryFn: () => withAuth(listOwnCertifications), enabled: owner })
  /** The row being edited, or 'new'. */
  const [editing, setEditing] = useState<string | 'new' | null>(null)

  const rows: (PublicCertification | Certification)[] = (owner ? own.data?.items : visitor.data?.items) ?? []
  if (!owner && rows.length === 0) return null

  return (
    <section aria-labelledby="certifications-heading" className="rounded-2xl bg-white p-4 shadow-sm ring-1 ring-paper-200 sm:p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 id="certifications-heading" className="text-lg leading-tight font-semibold text-stone-900">
            Certifications and safety
          </h2>
          <p className="mt-0.5 text-sm text-stone-500">Checked against original documents</p>
        </div>
        {owner && editing !== 'new' && (
          <button
            type="button"
            onClick={() => setEditing('new')}
            className="rounded-full border border-paper-300 px-3 py-1.5 text-sm font-medium text-ink-900 hover:border-ink-900"
          >
            + Upload certificate
          </button>
        )}
      </div>

      {owner && editing === 'new' && <CertificationForm onDone={() => setEditing(null)} guideId={guide.id} />}

      {owner && own.isSuccess && rows.length === 0 && editing !== 'new' && (
        <p className="mt-4 text-sm text-stone-600">
          Add your courses and certificates with a scan or photo of each. Empty Valley checks them before they show on your page.
        </p>
      )}

      {rows.length > 0 && (
        <ul className="mt-4 divide-y divide-paper-200">
          {rows.map((c) => (
            <li key={c.id} className="py-3">
              <CertificationRow cert={c} onEdit={owner && editing !== c.id ? () => setEditing(c.id) : undefined} />
              {owner && editing === c.id && (
                <CertificationForm cert={c as Certification} onDone={() => setEditing(null)} guideId={guide.id} />
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

function CertificationRow({ cert: c, onEdit }: { cert: PublicCertification | Certification; onEdit?: () => void }) {
  const when = c.valid_to ? `valid to ${monthYear(c.valid_to)}` : c.year !== null ? String(c.year) : null
  return (
    <div className="flex items-start gap-3">
      <ShieldIcon className={`mt-0.5 size-5 shrink-0 ${c.status === 'VERIFIED' ? 'text-brand-700' : 'text-stone-400'}`} />
      <div className="min-w-0 flex-1">
        <p className="font-medium text-stone-900">{certificationTitle(c)}</p>
        {c.issuer && <p className="text-sm text-stone-600">{c.issuer}</p>}
        {(when || c.number) && (
          <p className="mt-0.5 font-mono text-xs text-stone-500">{[when, c.number && `No. ${c.number}`].filter(Boolean).join(' · ')}</p>
        )}
      </div>
      <StatusPill status={c.status} />
      {onEdit && (
        <button type="button" onClick={onEdit} className="text-sm font-medium text-brand-700 hover:text-brand-900">
          Edit
        </button>
      )}
    </div>
  )
}

const PILL: Record<CertificationStatus, { label: string; className: string }> = {
  VERIFIED: { label: 'Verified', className: 'bg-brand-100 text-brand-900' },
  EXPIRED: { label: 'Expired', className: 'bg-laterite-100 text-laterite-600' },
  PENDING: { label: 'Pending', className: 'bg-paper-200 text-stone-600' },
}

export function StatusPill({ status }: { status: CertificationStatus }) {
  const pill = PILL[status]
  return <span className={`shrink-0 rounded-full px-2.5 py-0.5 text-xs font-semibold ${pill.className}`}>{pill.label}</span>
}

/** "2027-03-31" → "Mar 2027". */
function monthYear(iso: string) {
  return new Date(`${iso}T00:00:00`).toLocaleDateString('en-IN', { month: 'short', year: 'numeric' })
}

type Draft = { type: CertificationType; title: string; issuer: string; year: string; valid_to: string; number: string }

const toDraft = (c?: Certification): Draft => ({
  type: c?.type ?? 'BMC',
  title: c?.title ?? '',
  issuer: c?.issuer ?? '',
  year: c?.year != null ? String(c.year) : '',
  valid_to: c?.valid_to ?? '',
  number: c?.number ?? '',
})

const toInput = (d: Draft): CertificationInput => ({
  type: d.type,
  title: d.title.trim() || null,
  issuer: d.issuer.trim() || null,
  year: d.year ? Number(d.year) : null,
  valid_to: d.valid_to || null,
  number: d.number.trim() || null,
})

/** Add or edit one certificate, with its document. Saving any change sends it back for checking. */
function CertificationForm({ cert, guideId, onDone }: { cert?: Certification; guideId: string; onDone: () => void }) {
  const { withAuth } = useAuth()
  const queryClient = useQueryClient()
  const [draft, setDraft] = useState<Draft>(() => toDraft(cert))
  const [file, setFile] = useState<File | null>(null)
  const [fileError, setFileError] = useState<string | null>(null)
  const idPrefix = `cert-${cert?.id ?? 'new'}`
  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: OWN_KEY })
    void queryClient.invalidateQueries({ queryKey: credentialsKey(guideId) })
  }

  const save = useMutation({
    mutationFn: () =>
      withAuth(async (token) => {
        const saved = cert ? await updateCertification(token, cert.id, toInput(draft)) : await createCertification(token, toInput(draft))
        return file ? uploadCertificationDocument(token, saved.id, file) : saved
      }),
    onSuccess: () => {
      refresh()
      onDone()
    },
  })
  const remove = useMutation({
    mutationFn: () => withAuth((token) => deleteCertification(token, cert!.id)),
    onSuccess: () => {
      refresh()
      onDone()
    },
  })
  const view = useMutation({ mutationFn: (path: string) => withAuth((token) => openCertificationDocument(token, path)) })

  const errors = fieldErrors(save.error)
  const set = (key: keyof Draft) => (e: { target: { value: string } }) => setDraft({ ...draft, [key]: e.target.value })
  const pickFile = (picked: File | null) => {
    setFileError(picked && picked.size > MAX_DOCUMENT_BYTES ? 'The file must be 5 MB or smaller' : null)
    setFile(picked)
  }
  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (!fileError) save.mutate()
  }

  return (
    <form onSubmit={submit} className="mt-3 space-y-4 rounded-xl bg-paper-50 p-4 ring-1 ring-paper-200">
      <SelectField
        label="Certificate"
        name={`${idPrefix}-type`}
        value={draft.type}
        onChange={set('type')}
        options={CERTIFICATION_TYPES.map((t) => ({ value: t, label: CERTIFICATION_LABELS[t] }))}
      />
      <TextField
        id={`${idPrefix}-title`}
        label={draft.type === 'OTHER' ? 'Name of the certificate' : 'Name on the certificate (optional)'}
        name="title"
        maxLength={120}
        required={draft.type === 'OTHER'}
        value={draft.title}
        onChange={set('title')}
        error={errors.title}
      />
      <TextField
        id={`${idPrefix}-issuer`}
        label="Issued by"
        name="issuer"
        maxLength={160}
        placeholder="Nehru Institute of Mountaineering, Uttarkashi"
        value={draft.issuer}
        onChange={set('issuer')}
        error={errors.issuer}
      />
      <div className="grid gap-4 sm:grid-cols-3">
        <TextField
          id={`${idPrefix}-year`}
          label="Year"
          name="year"
          type="number"
          min={1950}
          max={new Date().getFullYear()}
          value={draft.year}
          onChange={set('year')}
          error={errors.year}
        />
        <TextField
          id={`${idPrefix}-valid-to`}
          label="Valid to"
          name="valid_to"
          type="date"
          value={draft.valid_to}
          onChange={set('valid_to')}
          error={errors.valid_to}
          hint="For first-aid courses and insurance"
        />
        <TextField
          id={`${idPrefix}-number`}
          label="Certificate no."
          name="number"
          maxLength={60}
          value={draft.number}
          onChange={set('number')}
          error={errors.number}
        />
      </div>
      <div>
        <label htmlFor={`${idPrefix}-file`} className="block text-sm font-medium text-stone-800">
          {cert?.document_url ? 'Replace the document' : 'Scan or photo of the certificate'}
        </label>
        <input
          id={`${idPrefix}-file`}
          type="file"
          accept={DOCUMENT_TYPES}
          onChange={(e) => pickFile(e.target.files?.[0] ?? null)}
          className="mt-1.5 block w-full text-sm text-stone-700 file:mr-3 file:rounded-full file:border-0 file:bg-brand-100 file:px-3 file:py-1.5 file:text-sm file:font-medium file:text-brand-900"
        />
        <p className="mt-1 text-sm text-stone-500">
          PDF, JPEG or PNG up to 5 MB. Only Empty Valley sees it, never visitors.
          {cert?.document_url && (
            <>
              {' '}
              <button type="button" onClick={() => view.mutate(cert.document_url!)} className="font-medium text-brand-700 hover:text-brand-900">
                View current
              </button>
            </>
          )}
        </p>
        {fileError && <p className="mt-1 text-sm text-laterite-600">{fileError}</p>}
      </div>
      {cert?.status === 'VERIFIED' && <p className="text-sm text-stone-600">Saving a change sends it back to Empty Valley to check again.</p>}
      {[save.error, remove.error, view.error].map(
        (error, i) =>
          error && Object.keys(fieldErrors(error)).length === 0 && (
            <p key={i} role="alert" className="text-sm text-laterite-600">
              {error.message}
            </p>
          ),
      )}
      <div className="flex flex-wrap items-center gap-3">
        <button
          type="submit"
          disabled={save.isPending}
          className="rounded-full bg-brand-900 px-4 py-2 text-sm font-medium text-white hover:bg-brand-800 disabled:opacity-60"
        >
          {save.isPending ? 'Saving…' : 'Save'}
        </button>
        <button type="button" onClick={onDone} className="text-sm font-medium text-stone-600 hover:text-stone-900">
          Cancel
        </button>
        {cert && (
          <button
            type="button"
            disabled={remove.isPending}
            onClick={() => {
              if (window.confirm(`Remove ${certificationTitle(cert)}?`)) remove.mutate()
            }}
            className="ml-auto text-sm font-medium text-laterite-600 hover:text-laterite-500"
          >
            Remove
          </button>
        )}
      </div>
    </form>
  )
}

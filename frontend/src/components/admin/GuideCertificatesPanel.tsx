import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import {
  certificationTitle,
  listGuideCertifications,
  openCertificationDocument,
  setCertificationVerified,
} from '../../api/certifications.ts'
import { useAuth } from '../../auth/useAuth.ts'
import { StatusPill } from '../guide/sections/CertificationsSection.tsx'
import { Button, ErrorNote, Loading } from './AdminUi.tsx'

/** A guide's certificates (docs/TRD.md §7.21.3): open each document, then verify or unverify it (audit-logged). */
export function GuideCertificatesPanel({ guideId }: { guideId: string }) {
  const { withAuth } = useAuth()
  const queryClient = useQueryClient()
  const key = ['admin-guide-certifications', guideId]
  const list = useQuery({ queryKey: key, queryFn: () => withAuth((token) => listGuideCertifications(token, guideId)) })
  const [note, setNote] = useState('')
  const toggle = useMutation({
    mutationFn: ({ id, verified }: { id: string; verified: boolean }) =>
      withAuth((token) => setCertificationVerified(token, guideId, id, verified, note.trim() || null)),
    onSuccess: () => {
      setNote('')
      void queryClient.invalidateQueries({ queryKey: key })
    },
  })
  const view = useMutation({ mutationFn: (path: string) => withAuth((token) => openCertificationDocument(token, path)) })

  if (list.isPending) return <Loading />
  if (list.isError) return <ErrorNote error={list.error} />
  if (list.data.items.length === 0) return <p className="mt-3 text-sm text-stone-600">No certificates added yet.</p>

  return (
    <div className="mt-3 space-y-3">
      <ul className="divide-y divide-stone-100 rounded-lg border border-stone-200">
        {list.data.items.map((c) => (
          <li key={c.id} className="flex flex-wrap items-center gap-3 p-3">
            <div className="min-w-0 flex-1">
              <p className="font-medium">{certificationTitle(c)}</p>
              <p className="text-sm text-stone-500">
                {[c.issuer, c.year, c.valid_to && `valid to ${c.valid_to}`, c.number && `No. ${c.number}`].filter(Boolean).join(' · ')}
              </p>
            </div>
            <StatusPill status={c.status} />
            {c.document_url ? (
              <Button tone="secondary" onClick={() => view.mutate(c.document_url!)}>
                Document
              </Button>
            ) : (
              <span className="text-sm text-stone-500">No document</span>
            )}
            <Button
              tone={c.verified_at ? 'danger' : 'primary'}
              disabled={toggle.isPending}
              onClick={() => toggle.mutate({ id: c.id, verified: !c.verified_at })}
            >
              {c.verified_at ? 'Unverify' : 'Verify'}
            </Button>
          </li>
        ))}
      </ul>
      <label className="block text-sm">
        <span className="text-stone-700">Note for the audit log (optional, sent with the next Verify/Unverify)</span>
        <input
          value={note}
          maxLength={500}
          onChange={(e) => setNote(e.target.value)}
          className="mt-1 w-full rounded-md border border-stone-300 px-3 py-2"
        />
      </label>
      {(toggle.error || view.error) && <ErrorNote error={toggle.error ?? view.error} />}
    </div>
  )
}

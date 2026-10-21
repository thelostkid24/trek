import type { Items } from './catalog.ts'
import { apiFetch, apiFetchBlob } from './client.ts'

// Guide certifications and tiers (docs/TRD.md §7.21.3). Guides edit their own under /api/guide, admins verify
// under /api/admin, and the public page reads /api/public/guides/{key}/credentials.

export const CERTIFICATION_TYPES = [
  'BMC',
  'AMC',
  'SEARCH_RESCUE',
  'MOI',
  'WILDERNESS_FIRST_RESPONDER',
  'FIRST_AID',
  'POLICE_VERIFICATION',
  'INSURANCE',
  'OTHER',
] as const

export type CertificationType = (typeof CERTIFICATION_TYPES)[number]

export const CERTIFICATION_LABELS: Record<CertificationType, string> = {
  BMC: 'Basic Mountaineering Course',
  AMC: 'Advanced Mountaineering Course',
  SEARCH_RESCUE: 'Search & Rescue',
  MOI: 'Method of Instruction',
  WILDERNESS_FIRST_RESPONDER: 'Wilderness First Responder',
  FIRST_AID: 'First aid',
  POLICE_VERIFICATION: 'Police verification',
  INSURANCE: 'Insurance',
  OTHER: 'Other certificate',
}

/** EXPIRED past valid-to (verified or not); VERIFIED or PENDING otherwise. */
export type CertificationStatus = 'VERIFIED' | 'EXPIRED' | 'PENDING'

export type GuideTier = 'TRAIL_GUIDE' | 'SENIOR_GUIDE' | 'SUMMIT_GUIDE'

export const TIER_LABELS: Record<GuideTier, string> = {
  TRAIL_GUIDE: 'Trail Guide',
  SENIOR_GUIDE: 'Senior Guide',
  SUMMIT_GUIDE: 'Summit Guide',
}

export type Certification = {
  id: string
  type: CertificationType
  title: string | null
  issuer: string | null
  year: number | null
  /** ISO date; the last day it's valid. */
  valid_to: string | null
  number: string | null
  status: CertificationStatus
  /** API path that needs the caller's token; null with no document. */
  document_url: string | null
  verified_at: string | null
  created_at: string
  updated_at: string
}

export type CertificationInput = {
  type: CertificationType
  title: string | null
  issuer: string | null
  year: number | null
  valid_to: string | null
  number: string | null
}

/** Verified certificates only (expired included); `number` only for BMC/AMC; never the document. */
export type PublicCertification = Pick<Certification, 'id' | 'type' | 'title' | 'issuer' | 'year' | 'valid_to' | 'number' | 'status'>

export type GuideCredentials = {
  tier: GuideTier | null
  /** Any verified, unexpired certificate. */
  verified: boolean
  items: PublicCertification[]
}

/** PDF, JPEG or PNG up to 5 MB. */
export const DOCUMENT_TYPES = 'application/pdf,image/jpeg,image/png'
export const MAX_DOCUMENT_BYTES = 5 * 1024 * 1024

export const certificationTitle = (c: Pick<Certification, 'type' | 'title'>) => c.title ?? CERTIFICATION_LABELS[c.type]

/** Query key shared by the page's TierBadge and CertificationsSection, so it's fetched once. */
export const credentialsKey = (guideId: string) => ['guide-credentials', guideId] as const

export const getGuideCredentials = (key: string) =>
  apiFetch<GuideCredentials>(`/api/public/guides/${encodeURIComponent(key)}/credentials`)

export const listOwnCertifications = (token: string) => apiFetch<Items<Certification>>('/api/guide/certifications', { token })

export const createCertification = (token: string, body: CertificationInput) =>
  apiFetch<Certification>('/api/guide/certifications', { method: 'POST', token, body })

export const updateCertification = (token: string, id: string, body: CertificationInput) =>
  apiFetch<Certification>(`/api/guide/certifications/${id}`, { method: 'PUT', token, body })

export const deleteCertification = (token: string, id: string) =>
  apiFetch<void>(`/api/guide/certifications/${id}`, { method: 'DELETE', token })

export const uploadCertificationDocument = (token: string, id: string, file: File) => {
  const body = new FormData()
  body.append('file', file, file.name)
  return apiFetch<Certification>(`/api/guide/certifications/${id}/document`, { method: 'PUT', token, body })
}

export const listGuideCertifications = (token: string, guideId: string) =>
  apiFetch<Items<Certification>>(`/api/admin/guides/${guideId}/certifications`, { token })

export const setCertificationVerified = (token: string, guideId: string, id: string, verified: boolean, note: string | null) =>
  apiFetch<Certification>(`/api/admin/guides/${guideId}/certifications/${id}/${verified ? 'verify' : 'unverify'}`, {
    method: 'POST',
    token,
    body: { note },
  })

/** Opens a document (`document_url` of the guide's own or the admin list) in a new tab. */
export async function openCertificationDocument(token: string, path: string) {
  // Open the tab before awaiting, so popup blockers treat it as the click's.
  const tab = window.open('', '_blank')
  try {
    const url = URL.createObjectURL(await apiFetchBlob(path, token))
    if (tab) tab.location.href = url
    else window.location.assign(url)
    setTimeout(() => URL.revokeObjectURL(url), 60_000)
  } catch (e) {
    tab?.close()
    throw e
  }
}

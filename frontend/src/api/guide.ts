import { apiFetch } from './client.ts'

// The guide's own profile (docs/TRD.md §7.21). The guide is the token's subject.

export type Milestone = { year: number; text: string }
export type RouteNote = { track_id: string; note: string }

export type OwnGuideProfile = {
  guide_id: string
  /** The public profile is /guides/{slug}; only an admin changes it. */
  slug: string
  full_name: string | null
  avatar_url: string | null
  cover_url: string | null
  home_city: string | null
  bio: string | null
  /** The intro line under the name. */
  quote: string | null
  languages: string | null
  leading_since: number | null
  specialty_ids: string[]
  milestones: Milestone[]
  route_notes: RouteNote[]
  /** The list to pick specialties from (read-only). */
  specialty_options: { id: string; name: string }[]
  /** Treks the guide may write a note about (read-only). */
  routes: { track_id: string; name: string }[]
}

export type OwnGuideProfileInput = {
  full_name: string
  home_city: string | null
  bio: string | null
  quote: string | null
  languages: string | null
  leading_since: number | null
  specialty_ids: string[]
  milestones: Milestone[]
  route_notes: RouteNote[]
}

/** Most specialties a guide can pick. */
export const MAX_SPECIALTIES = 8

export const getOwnGuideProfile = (token: string) => apiFetch<OwnGuideProfile>('/api/guide/profile', { token })

export const updateOwnGuideProfile = (token: string, body: OwnGuideProfileInput) =>
  apiFetch<OwnGuideProfile>('/api/guide/profile', { method: 'PUT', token, body })

function upload(path: string, token: string, file: Blob) {
  const body = new FormData()
  body.append('file', file, 'photo.jpg')
  return apiFetch<OwnGuideProfile>(path, { method: 'PUT', token, body })
}

export const uploadOwnGuidePhoto = (token: string, file: Blob) => upload('/api/guide/profile/avatar', token, file)

export const removeOwnGuidePhoto = (token: string) =>
  apiFetch<OwnGuideProfile>('/api/guide/profile/avatar', { method: 'DELETE', token })

export const uploadOwnGuideCover = (token: string, file: Blob) => upload('/api/guide/profile/cover', token, file)

export const removeOwnGuideCover = (token: string) =>
  apiFetch<OwnGuideProfile>('/api/guide/profile/cover', { method: 'DELETE', token })

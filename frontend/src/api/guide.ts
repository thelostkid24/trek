import { apiFetch } from './client.ts'

// The guide's own profile (docs/TRD.md §7.21). The guide is the token's subject.

/**
 * "Career in the mountains": a date as exact as known — none ("—"), a year, "~" a year (`approximate`), a month or
 * a day — and a line.
 */
export type Milestone = { year: number | null; month: number | null; day: number | null; approximate: boolean; text: string }

export type GuideRouteKind = 'TREK' | 'PEAK'
/** ANY_TIME may carry a season of months; FROM_DATE needs `available_from`. */
export type RouteAvailability = 'ANY_TIME' | 'FROM_DATE' | 'NOT_OPEN' | 'ASK'

/** When a guide can lead a route, and their note on it: the part the guide edits (docs/TRD.md §7.21.8). */
export type RoutePlan = {
  availability: RouteAvailability
  season_from_month: number | null
  season_to_month: number | null
  /** ISO date. */
  available_from: string | null
  note: string | null
}

/**
 * A route a guide leads: a catalog trek (`track_id`, `track_name`) or one we don't list (`name`). `id` is null for a
 * trek they've led or lead with us that has no row yet. `times_led` and `last_led_*` are from before Sahyātri.
 */
export type RouteItem = RoutePlan & {
  id: string | null
  track_id: string | null
  track_name: string | null
  name: string | null
  kind: GuideRouteKind | null
  times_led: number | null
  last_led_year: number | null
  last_led_month: number | null
  most_recent: boolean
}

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
  /** The list to pick specialties from (read-only). */
  specialty_options: { id: string; name: string }[]
  /** Their routes, then treks they've led or lead with us without a row yet; they edit the plan part only. */
  routes: RouteItem[]
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
  /** Only the routes named change; leave out to keep them all as they are. */
  routes: (RoutePlan & { id: string | null; track_id: string | null })[]
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

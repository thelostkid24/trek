import { apiFetch } from './client'
import type { PledgeKind } from './certifications.ts'
import type { RouteItem } from './guide.ts'
import type {
  ContentItem,
  GuideStatKey,
  ContentKind,
  DepartureStatus,
  Difficulty,
  ItineraryDay,
  Items,
  PhotoLicence,
  PlannedDate,
  SnowReport,
  Snowfall,
  TrackPhoto,
  TrekContent,
} from './catalog'

// Contract: docs/TRD.md §7.5 (admin). Call through `withAuth` from useAuth().

export type Track = {
  id: string
  slug: string
  name: string
  region: string
  /** Indian state or union territory; null until an admin picks one. */
  state: string | null
  difficulty: Difficulty
  duration_days: number
  max_altitude_m: number | null
  summary: string
  description: string
  meeting_point: string
  distance_km: number | null
  base_altitude_m: number | null
  highest_camp_m: number | null
  stay: string | null
  season_label: string | null
  pickup_drop: string | null
  cloakroom: boolean | null
  offloading: boolean | null
  /** Null with offloading = paid, price not fixed yet. */
  offloading_price_paise: number | null
  /** Per-seat add-on prices at checkout; null = not offered. Offloading's uses `offloading_price_paise`. */
  insurance_price_paise: number | null
  transport_price_paise: number | null
  /** Shown in the public catalog even with no upcoming dates; treks with dates always show. */
  listed: boolean
  /** One entry per day, day 1 first; empty or exactly `duration_days` entries. */
  itinerary: ItineraryDay[]
  /** Managed with uploadTrackPhoto / deleteTrackPhoto, not the track form. */
  photos: TrackPhoto[]
  created_at: string
  updated_at: string
}

export type ItineraryDayInput = Omit<ItineraryDay, 'day'>

/** `listed` has its own endpoint (setTrackListed), so the track form never sends it. */
export type TrackInput = Omit<Track, 'id' | 'photos' | 'listed' | 'itinerary' | 'created_at' | 'updated_at'> & {
  itinerary: ItineraryDayInput[]
}

export type CancelReason = 'WEATHER' | 'PERMIT_DENIED' | 'GUIDE_UNAVAILABLE' | 'SAFETY'

export const CANCEL_REASON_LABEL: Record<CancelReason, string> = {
  WEATHER: 'Weather',
  PERMIT_DENIED: 'Permit denied',
  GUIDE_UNAVAILABLE: 'Guide unavailable, no substitute',
  SAFETY: 'Safety',
}

export type AdminDeparture = {
  id: string
  track: { id: string; slug: string; name: string; duration_days: number }
  guide: { id: string; full_name: string | null; email: string | null; avatar_url: string | null }
  start_date: string
  end_date: string
  price_paise: number
  max_group_size: number
  seats_taken: number
  status: DepartureStatus
  guide_share_bps: number | null
  published_at: string | null
  cancelled_at: string | null
  cancel_reason_code: CancelReason | null
  cancel_reason_note: string | null
  created_at: string
  updated_at: string
}

export type DepartureInput = {
  track_id: string
  guide_id: string
  start_date: string
  price_paise: number
  max_group_size: number
}

export type Guide = {
  id: string
  full_name: string | null
  email: string | null
  phone: string | null
  avatar_url: string | null
  created_at: string
}

export const listTracks = (token: string) => apiFetch<Items<Track>>('/api/admin/tracks', { token })

export const createTrack = (token: string, body: TrackInput) =>
  apiFetch<Track>('/api/admin/tracks', { method: 'POST', token, body })

export const updateTrack = (token: string, id: string, body: TrackInput) =>
  apiFetch<Track>(`/api/admin/tracks/${id}`, { method: 'PUT', token, body })

export const setTrackListed = (token: string, id: string, listed: boolean) =>
  apiFetch<Track>(`/api/admin/tracks/${id}/listed`, { method: 'PUT', token, body: { listed } })

export type PhotoWords = {
  caption: string | null
  place: string | null
  day_number: number | null
  credit: string | null
  licence: PhotoLicence | null
}

export const uploadTrackPhoto = (token: string, trackId: string, file: Blob, words: PhotoWords) => {
  const body = new FormData()
  body.append('file', file, 'photo.jpg')
  if (words.caption?.trim()) body.append('caption', words.caption.trim())
  if (words.place?.trim()) body.append('place', words.place.trim())
  if (words.day_number) body.append('day_number', String(words.day_number))
  if (words.credit?.trim()) body.append('credit', words.credit.trim())
  if (words.licence) body.append('licence', words.licence)
  return apiFetch<TrackPhoto>(`/api/admin/tracks/${trackId}/photos`, { method: 'POST', token, body })
}

export const describeTrackPhoto = (token: string, trackId: string, photoId: string, body: PhotoWords) =>
  apiFetch<TrackPhoto>(`/api/admin/tracks/${trackId}/photos/${photoId}`, { method: 'PUT', token, body })

export const deleteTrackPhoto = (token: string, trackId: string, photoId: string) =>
  apiFetch<void>(`/api/admin/tracks/${trackId}/photos/${photoId}`, { method: 'DELETE', token })

export const listAdminDepartures = (token: string) =>
  apiFetch<Items<AdminDeparture>>('/api/admin/departures', { token })

export const createDeparture = (token: string, body: DepartureInput) =>
  apiFetch<AdminDeparture>('/api/admin/departures', { method: 'POST', token, body })

export const updateDeparture = (token: string, id: string, body: DepartureInput) =>
  apiFetch<AdminDeparture>(`/api/admin/departures/${id}`, { method: 'PUT', token, body })

export const deleteDeparture = (token: string, id: string) =>
  apiFetch<void>(`/api/admin/departures/${id}`, { method: 'DELETE', token })

export const publishDeparture = (token: string, id: string) =>
  apiFetch<AdminDeparture>(`/api/admin/departures/${id}/publish`, { method: 'POST', token })

/** Published and not started yet; bookings already made keep the price they were made at. */
export const changeDeparturePrice = (token: string, id: string, pricePaise: number) =>
  apiFetch<AdminDeparture>(`/api/admin/departures/${id}/price`, { method: 'PUT', token, body: { price_paise: pricePaise } })

export const cancelDeparture = (token: string, id: string, body: { reason_code: CancelReason; reason_note: string }) =>
  apiFetch<AdminDeparture>(`/api/admin/departures/${id}/cancel`, { method: 'POST', token, body })

export const listGuides = (token: string) => apiFetch<Items<Guide>>('/api/admin/guides', { token })

export const promoteGuide = (token: string, email: string) =>
  apiFetch<Guide>('/api/admin/guides', { method: 'POST', token, body: { email } })

// Planned dates: when a trek runs before its guide is confirmed (docs/TRD.md §7.20). Replaced whole.

export type PlannedDateInput = { start_date: string; note: string | null }

export const listPlannedDates = (token: string, trackId: string) =>
  apiFetch<Items<PlannedDate>>(`/api/admin/tracks/${trackId}/planned-dates`, { token })

export const replacePlannedDates = (token: string, trackId: string, items: PlannedDateInput[]) =>
  apiFetch<Items<PlannedDate>>(`/api/admin/tracks/${trackId}/planned-dates`, { method: 'PUT', token, body: { items } })

// Trek-page lists (docs/TRD.md §7.11). No track id = the shared lists shown on every trek.

const contentPath = (trackId: string | null) =>
  trackId ? `/api/admin/tracks/${trackId}/content` : '/api/admin/content'

export const getContent = (token: string, trackId: string | null) => apiFetch<TrekContent>(contentPath(trackId), { token })

export const replaceContent = (token: string, trackId: string | null, kind: ContentKind, items: ContentItem[]) =>
  apiFetch<TrekContent>(`${contentPath(trackId)}/${kind}`, { method: 'PUT', token, body: { items } })

// Guide credentials (docs/TRD.md §7.12).

export type GuideDetails = {
  guide_id: string
  leading_since: number | null
  years_leading: number | null
  languages: string | null
  certification: string | null
  certification_number: string | null
  bmc_institute: string | null
  bmc_certificate_number: string | null
  amc_institute: string | null
  amc_certificate_number: string | null
  quote: string | null
  /** Shows "Route verified" on the guides list whatever their treks led (docs/TRD.md §7.17). */
  route_verified: boolean
  /** Catalog treks with times led before Sahyātri, most-led first (read-only; set in the record). */
  prior_treks: { track_id: string; times: number }[]
}

export type GuideDetailsInput = Omit<GuideDetails, 'guide_id' | 'years_leading' | 'prior_treks'>

export const getGuideDetails = (token: string, guideId: string) =>
  apiFetch<GuideDetails>(`/api/admin/guides/${guideId}/details`, { token })

export const updateGuideDetails = (token: string, guideId: string, body: GuideDetailsInput) =>
  apiFetch<GuideDetails>(`/api/admin/guides/${guideId}/details`, { method: 'PUT', token, body })

// Guide record: routes, pledges, picked stats, treks in the last 3 years (docs/TRD.md §7.21.8).

/** `route_index`: for ROUTE_TIMES, the route's place in `routes`. */
/** `value` and `label`: what a CUSTOM stat shows, as typed; null for the other kinds (docs/TRD.md §7.21.8). */
export type StatPick = { key: GuideStatKey; route_index: number | null; value: string | null; label: string | null }

export type GuideRecord = {
  treks_last_3y: number | null
  routes: RouteItem[]
  pledges: PledgeKind[]
  /** Empty: the page shows its default row. */
  stats: StatPick[]
}

/** `id` keeps an existing route (and stats pointing at it); null adds one. */
export type GuideRecordInput = {
  treks_last_3y: number | null
  routes: Omit<RouteItem, 'track_name'>[]
  pledges: PledgeKind[]
  stats: StatPick[]
}

export const getGuideRecord = (token: string, guideId: string) =>
  apiFetch<GuideRecord>(`/api/admin/guides/${guideId}/record`, { token })

export const updateGuideRecord = (token: string, guideId: string, body: GuideRecordInput) =>
  apiFetch<GuideRecord>(`/api/admin/guides/${guideId}/record`, { method: 'PUT', token, body })

// Guide profile: name, photo, home city and bio, set by an admin (docs/TRD.md §7.12).

export type GuideProfile = {
  guide_id: string
  /** The public profile is /guides/{slug} (docs/TRD.md §7.21). */
  slug: string
  full_name: string | null
  avatar_url: string | null
  home_city: string | null
  bio: string | null
}

export type GuideProfileInput = { full_name: string; home_city: string | null; bio: string | null }

export const getGuideProfile = (token: string, guideId: string) =>
  apiFetch<GuideProfile>(`/api/admin/guides/${guideId}/profile`, { token })

export const updateGuideProfile = (token: string, guideId: string, body: GuideProfileInput) =>
  apiFetch<GuideProfile>(`/api/admin/guides/${guideId}/profile`, { method: 'PUT', token, body })

export function uploadGuidePhoto(token: string, guideId: string, file: Blob) {
  const body = new FormData()
  body.append('file', file, 'photo.jpg')
  return apiFetch<GuideProfile>(`/api/admin/guides/${guideId}/avatar`, { method: 'PUT', token, body })
}

export const removeGuidePhoto = (token: string, guideId: string) =>
  apiFetch<GuideProfile>(`/api/admin/guides/${guideId}/avatar`, { method: 'DELETE', token })

/** A new profile address; the old one keeps working (docs/TRD.md §7.21). */
export const changeGuideSlug = (token: string, guideId: string, slug: string) =>
  apiFetch<GuideProfile>(`/api/admin/guides/${guideId}/slug`, { method: 'PUT', token, body: { slug } })

// The specialties guides pick from (docs/TRD.md §7.21).

export type GuideSpecialty = { id: string; name: string }

export const listGuideSpecialties = (token: string) =>
  apiFetch<Items<GuideSpecialty>>('/api/admin/guide-specialties', { token })

export const createGuideSpecialty = (token: string, name: string) =>
  apiFetch<GuideSpecialty>('/api/admin/guide-specialties', { method: 'POST', token, body: { name } })

export const deleteGuideSpecialty = (token: string, id: string) =>
  apiFetch<void>(`/api/admin/guide-specialties/${id}`, { method: 'DELETE', token })

// Snow reports (docs/TRD.md §7.13). Admins use /api/admin, guides /api/guide (see api/guide.ts).

export type SnowReportInput = {
  reported_on: string
  reported_from: string
  snowline_m: number | null
  night_temp_c: number | null
  snowfall: Snowfall | null
  conditions: { label: string; value: string }[]
  crowd_place: string | null
  crowd_tents: number | null
  note: string | null
}

export type SnowReportApi = {
  list: (token: string, trackId: string) => Promise<Items<SnowReport>>
  create: (token: string, trackId: string, body: SnowReportInput) => Promise<SnowReport>
  addPhoto: (token: string, reportId: string, file: Blob) => Promise<SnowReport>
}

export const snowReportApi = (area: 'admin' | 'guide'): SnowReportApi => ({
  list: (token, trackId) => apiFetch<Items<SnowReport>>(`/api/${area}/tracks/${trackId}/snow-reports`, { token }),
  create: (token, trackId, body) =>
    apiFetch<SnowReport>(`/api/${area}/tracks/${trackId}/snow-reports`, { method: 'POST', token, body }),
  addPhoto: (token, reportId, file) => {
    const body = new FormData()
    body.append('file', file, 'photo.jpg')
    return apiFetch<SnowReport>(`/api/${area}/snow-reports/${reportId}/photo`, { method: 'POST', token, body })
  },
})

/** Treks the signed-in guide can report on. */
export const listGuideTracks = (token: string) =>
  apiFetch<Items<{ id: string; slug: string; name: string }>>('/api/guide/tracks', { token })

import { apiFetch } from './client'
import type {
  ContentItem,
  ContentKind,
  DepartureStatus,
  Difficulty,
  ItineraryDay,
  Items,
  SnowReport,
  TrackPhoto,
  TrekContent,
} from './catalog'

// Contract: docs/TRD.md §7.5 (admin). Call through `withAuth` from useAuth().

export type Track = {
  id: string
  slug: string
  name: string
  region: string
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

export type PhotoWords = { caption: string | null; place: string | null; day_number: number | null }

export const uploadTrackPhoto = (token: string, trackId: string, file: Blob, words: PhotoWords) => {
  const body = new FormData()
  body.append('file', file, 'photo.jpg')
  if (words.caption?.trim()) body.append('caption', words.caption.trim())
  if (words.place?.trim()) body.append('place', words.place.trim())
  if (words.day_number) body.append('day_number', String(words.day_number))
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

export const cancelDeparture = (token: string, id: string, body: { reason_code: CancelReason; reason_note: string }) =>
  apiFetch<AdminDeparture>(`/api/admin/departures/${id}/cancel`, { method: 'POST', token, body })

export const listGuides = (token: string) => apiFetch<Items<Guide>>('/api/admin/guides', { token })

export const promoteGuide = (token: string, email: string) =>
  apiFetch<Guide>('/api/admin/guides', { method: 'POST', token, body: { email } })

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
  quote: string | null
}

export type GuideDetailsInput = Omit<GuideDetails, 'guide_id' | 'years_leading'>

export const getGuideDetails = (token: string, guideId: string) =>
  apiFetch<GuideDetails>(`/api/admin/guides/${guideId}/details`, { token })

export const updateGuideDetails = (token: string, guideId: string, body: GuideDetailsInput) =>
  apiFetch<GuideDetails>(`/api/admin/guides/${guideId}/details`, { method: 'PUT', token, body })

// Snow reports (docs/TRD.md §7.13). Admins use /api/admin, guides /api/guide (see api/guide.ts).

export type SnowReportInput = {
  reported_on: string
  reported_from: string
  snowline_m: number | null
  night_temp_c: number | null
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

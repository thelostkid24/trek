import { apiFetch } from './client'
import type { DepartureStatus, Difficulty, Items, TrackPhoto } from './catalog'

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
  /** Shown in the public catalog even with no upcoming dates; treks with dates always show. */
  listed: boolean
  /** One line per day, day 1 first; empty or exactly `duration_days` lines. */
  itinerary: string[]
  /** Managed with uploadTrackPhoto / deleteTrackPhoto, not the track form. */
  photos: TrackPhoto[]
  created_at: string
  updated_at: string
}

/** `listed` has its own endpoint (setTrackListed), so the track form never sends it. */
export type TrackInput = Omit<Track, 'id' | 'photos' | 'listed' | 'created_at' | 'updated_at'>

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

export const uploadTrackPhoto = (token: string, trackId: string, file: Blob, caption: string) => {
  const body = new FormData()
  body.append('file', file, 'photo.jpg')
  if (caption.trim()) body.append('caption', caption.trim())
  return apiFetch<TrackPhoto>(`/api/admin/tracks/${trackId}/photos`, { method: 'POST', token, body })
}

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

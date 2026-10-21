import { apiFetch } from './client.ts'

// A trekker's saved guides, "My Fav" (docs/TRD.md §7.21.7). Private: no public count, the guide isn't told.

export type SavedGuide = {
  guide_id: string
  /** Null until the guide's profile has an address; `guidePath` falls back to the id. */
  slug: string | null
  full_name: string | null
  avatar_url: string | null
  home_city: string | null
  rating: number | null
  review_count: number
  saved_at: string
}

export const SAVED_GUIDES_KEY = ['saved-guides']

export const listSavedGuides = (token: string) =>
  apiFetch<{ items: SavedGuide[] }>('/api/trekker/saved-guides', { token })

export const saveGuide = (token: string, guideId: string) =>
  apiFetch<void>(`/api/trekker/saved-guides/${guideId}`, { method: 'PUT', token })

export const unsaveGuide = (token: string, guideId: string) =>
  apiFetch<void>(`/api/trekker/saved-guides/${guideId}`, { method: 'DELETE', token })

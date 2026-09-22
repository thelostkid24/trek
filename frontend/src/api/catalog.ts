import { queryOptions } from '@tanstack/react-query'
import { apiFetch } from './client'

// Contract: docs/TRD.md §7.5 (public catalog).

/** Law 2 (docs/TRD.md §5): at most this many trekkers per guide. */
export const MAX_GROUP_SIZE = 10

export type Difficulty = 'EASY' | 'MODERATE' | 'CHALLENGING'
export type DepartureStatus = 'DRAFT' | 'PUBLISHED' | 'CANCELLED' | 'EXPIRED' | 'COMPLETED'

export const DIFFICULTY_LABEL: Record<Difficulty, string> = {
  EASY: 'Easy',
  MODERATE: 'Moderate',
  CHALLENGING: 'Challenging',
}

export type TrackBrief = {
  slug: string
  name: string
  region: string
  difficulty: Difficulty
  duration_days: number
}

export type GuideBrief = { id: string; full_name: string | null; avatar_url: string | null }

export type DepartureSummary = {
  id: string
  track: TrackBrief
  guide: GuideBrief
  start_date: string
  end_date: string
  price_paise: number
  max_group_size: number
  seats_left: number
  bookable: boolean
}

/** Route facts may be null until an admin fills them in. */
export type TrackDetail = TrackBrief & {
  summary: string
  description: string
  max_altitude_m: number | null
  meeting_point: string
  distance_km: number | null
  base_altitude_m: number | null
  highest_camp_m: number | null
  stay: string | null
  season_label: string | null
  itinerary: { day: number; summary: string }[]
  /** Photos from past runs, oldest upload first. */
  photos: TrackPhoto[]
}

export type TrackPhoto = { id: string; url: string; caption: string | null }

/** A departure's guide with their home and how often they've led this trek (completed runs). */
export type GuideCard = GuideBrief & { home_city: string | null; led_this_trek: number }

export type DepartureDetail = Omit<DepartureSummary, 'track' | 'guide'> & {
  status: Exclude<DepartureStatus, 'DRAFT'>
  track: TrackDetail
  guide: GuideCard
}

export type TrekDeparture = Omit<DepartureSummary, 'track' | 'guide'> & { guide: GuideCard }

/** Contract: docs/TRD.md §7.7. */
export type TrekPage = { track: TrackDetail; departures: TrekDeparture[] }

export type GuideProfile = {
  id: string
  full_name: string | null
  avatar_url: string | null
  home_city: string | null
  bio: string | null
  treks_led: number
  treks: { track: TrackBrief; times: number }[]
  upcoming: DepartureSummary[]
}

/** A departure as listed under its trek in the catalog. Contract: docs/TRD.md §7.9. */
export type CatalogDeparture = {
  id: string
  start_date: string
  end_date: string
  price_paise: number
  max_group_size: number
  seats_left: number
  bookable: boolean
  guide: GuideBrief
}

/** One trek in the catalog. Empty `departures` means "dates coming soon". */
export type CatalogTrek = {
  slug: string
  name: string
  region: string
  difficulty: Difficulty
  duration_days: number
  summary: string
  max_altitude_m: number | null
  season_label: string | null
  cover_url: string | null
  departures: CatalogDeparture[]
}

export type Items<T> = { items: T[] }

export const listDepartures = () => apiFetch<Items<DepartureSummary>>('/api/public/departures')

export const listCatalog = () => apiFetch<Items<CatalogTrek>>('/api/public/tracks')

export const getTrek = (slug: string) => apiFetch<TrekPage>(`/api/public/tracks/${encodeURIComponent(slug)}`)
/** Shared by the trek page and the home cards that prefetch it. */
export const trekQueryOptions = (slug: string) => queryOptions({ queryKey: ['public-trek', slug], queryFn: () => getTrek(slug) })

export const getGuide = (id: string) => apiFetch<GuideProfile>(`/api/public/guides/${encodeURIComponent(id)}`)

export const getDeparture = (id: string) =>
  apiFetch<DepartureDetail>(`/api/public/departures/${encodeURIComponent(id)}`)

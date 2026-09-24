import { queryOptions } from '@tanstack/react-query'
import { apiFetch } from './client'

// Contract: docs/TRD.md §7.5 (public catalog).

/** Law 2 (docs/TRD.md §5): at most this many trekkers per guide. */
export const MAX_GROUP_SIZE = 10

export type Difficulty = 'EASY' | 'EASY_MODERATE' | 'MODERATE' | 'CHALLENGING'
export type DepartureStatus = 'DRAFT' | 'PUBLISHED' | 'CANCELLED' | 'EXPIRED' | 'COMPLETED'

export const DIFFICULTY_LABEL: Record<Difficulty, string> = {
  EASY: 'Easy',
  EASY_MODERATE: 'Easy to moderate',
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

/** One day of the itinerary. Altitudes in metres run start → high point → end; any detail may be null. */
export type ItineraryDay = {
  day: number
  summary: string
  description: string | null
  distance_km: number | null
  start_altitude_m: number | null
  high_altitude_m: number | null
  end_altitude_m: number | null
  hours_min: number | null
  hours_max: number | null
  route_note: string | null
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
  pickup_drop: string | null
  cloakroom: boolean | null
  /** Paid bag offloading; `offloading_price_paise` null = price not fixed yet. */
  offloading: boolean | null
  offloading_price_paise: number | null
  itinerary: ItineraryDay[]
  /** Photos from past runs, oldest upload first. */
  photos: TrackPhoto[]
}

export type TrackPhoto = {
  id: string
  url: string
  /** "Summit ridge at first light". */
  caption: string | null
  /** "Kedarkantha summit". */
  place: string | null
  day_number: number | null
}

/** Credentials an admin fills in; null until then. */
export type GuideCredentials = {
  years_leading: number | null
  languages: string | null
  certification: string | null
  certification_number: string | null
  quote: string | null
}

/**
 * A departure's guide with their home, how often they've led this trek (completed runs), credentials and
 * rating (null until reviewed). Computed at read time.
 */
export type GuideCard = GuideBrief &
  GuideCredentials & {
    home_city: string | null
    led_this_trek: number
    rating: number | null
    review_count: number
  }

export type DepartureDetail = Omit<DepartureSummary, 'track' | 'guide'> & {
  status: Exclude<DepartureStatus, 'DRAFT'>
  track: TrackDetail
  guide: GuideCard
}

export type TrekDeparture = Omit<DepartureSummary, 'track' | 'guide'> & { guide: GuideCard }

/** The lists on a trek page (docs/TRD.md §7.11). Shared items come first. */
export type ContentKind = 'INCLUDED' | 'NOT_INCLUDED' | 'SAFETY' | 'SAFETY_CALLOUT' | 'SAFETY_NOTE' | 'FAQ' | 'WHY_US'
/** `title` is the question for FAQs and the heading for cards; `badge` is for WHY_US cards only. */
export type ContentItem = { badge: string | null; title: string | null; body: string }
export type TrekContent = Record<ContentKind, ContentItem[]>

/** A weekly trail report (docs/TRD.md §7.13). */
export type SnowReport = {
  id: string
  reported_on: string
  reported_from: string
  snowline_m: number | null
  night_temp_c: number | null
  conditions: { label: string; value: string }[]
  crowd_place: string | null
  crowd_tents: number | null
  note: string | null
  photo_url: string | null
  reported_by: { id: string; full_name: string | null }
  created_at: string
}

export type CrowdCount = { reported_on: string; place: string; tents: number }

export type RefundTier = { min_days_before: number; refund_bps: number }

/** Contract: docs/TRD.md §7.7. */
export type TrekPage = {
  track: TrackDetail
  departures: TrekDeparture[]
  content: TrekContent
  /** Newest report; null until one is filed. */
  snow_report: SnowReport | null
  /** Recent tent counts, oldest first. */
  crowd: CrowdCount[]
  /** Highest `min_days_before` first. */
  refund_tiers: RefundTier[]
  /** Included in the price; null when none is set. */
  charity: { name: string; bps: number } | null
}

export type PublicReview = {
  rating: number
  body: string | null
  author_name: string
  trek_name: string
  trek_start_date: string
  created_at: string
}

export type GuideProfile = GuideCredentials & {
  id: string
  full_name: string | null
  avatar_url: string | null
  home_city: string | null
  bio: string | null
  treks_led: number
  rating: number | null
  review_count: number
  reviews: PublicReview[]
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

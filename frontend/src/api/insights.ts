import { apiFetch } from './client'

// Contract: docs/TRD.md §7.15 — the admin Insights dashboard. Counts and sums only, never a person.

export type CountRow = { key: string; count: number }

export type SourceRow = {
  /** UTM source (or campaign), ad network, referring site, "direct" or "unknown". */
  source: string
  /** New accounts, by first touch. */
  accounts: number
  /** Bookings confirmed in the window, by the booking's own last touch. */
  confirmed_bookings: number
  gross_paise: number
}

export type Insights = {
  days: number
  from: string
  to: string
  headline: {
    new_accounts: number
    bookings_held: number
    bookings_confirmed: number
    gross_paise: number
    hold_to_paid_bps: number | null
    avg_group_size: number | null
    cancellations: number
  }
  daily: { date: string; accounts: number; confirmed: number }[]
  funnel: { key: string; label: string; count: number }[]
  sources: SourceRow[]
  campaigns: SourceRow[]
  heard_from: CountRow[]
  signup_methods: CountRow[]
  devices: CountRow[]
  payments: {
    attempts: number
    paid: number
    failed: number
    success_bps: number | null
    methods: CountRow[]
    failures: CountRow[]
  }
  treks: {
    track_id: string
    name: string
    slug: string
    confirmed_bookings: number
    seats: number
    gross_paise: number
    avg_rating: number | null
    reviews: number
  }[]
  upcoming: {
    departure_id: string
    track_name: string
    start_date: string
    seats_taken: number
    max_group_size: number
    guide_name: string | null
  }[]
  guides: {
    guide_id: string
    name: string | null
    departures_completed: number
    avg_fill_bps: number | null
    avg_rating: number | null
    reviews: number
  }[]
  marketing_reach: { accounts: number; email: number; whatsapp: number }
}

export const getInsights = (token: string, days: number) =>
  apiFetch<Insights>(`/api/admin/insights?days=${days}`, { token })

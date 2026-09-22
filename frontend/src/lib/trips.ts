import type { Booking } from '../api/bookings.ts'
import { parseDate, todayIst } from './format.ts'

// Splits a trekker's bookings for the account dashboard (docs/TRD.md §7.10).

/** A live booking whose trek hasn't finished yet. */
export const isUpcoming = (b: Booking, today = todayIst()) =>
  (b.status === 'HELD' || b.status === 'CONFIRMED') && b.departure.end_date >= today

/** A paid booking whose trek is over. */
export const isCompleted = (b: Booking, today = todayIst()) =>
  b.status === 'CONFIRMED' && b.departure.end_date < today

/** Upcoming bookings soonest first, everything else newest first. */
export function splitTrips(all: Booking[], today = todayIst()) {
  const upcoming = all
    .filter((b) => isUpcoming(b, today))
    .sort((a, b) => a.departure.start_date.localeCompare(b.departure.start_date))
  const past = all
    .filter((b) => !isUpcoming(b, today))
    .sort((a, b) => b.departure.start_date.localeCompare(a.departure.start_date))
  return { upcoming, past }
}

/** Whole calendar days from today (IST) to `iso`; negative once it has passed. */
export function daysUntil(iso: string, today = todayIst()): number {
  return Math.round((parseDate(iso).getTime() - parseDate(today).getTime()) / 86_400_000)
}

export function departsIn(iso: string): string {
  const days = daysUntil(iso)
  if (days <= 0) return 'Departs today'
  if (days === 1) return 'Departs tomorrow'
  return `Departs in ${days} days`
}

/** "6 days, 5 nights", or "1 day" for a day hike. */
export function daysAndNights(days: number): string {
  if (days <= 1) return '1 day'
  const nights = days - 1
  return `${days} days, ${nights} ${nights === 1 ? 'night' : 'nights'}`
}

export const monthYear = (iso: string) =>
  parseDate(iso).toLocaleDateString('en-IN', { month: 'long', year: 'numeric' })

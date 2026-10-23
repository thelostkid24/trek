import type { Milestone, RoutePlan } from '../api/guide.ts'

// Wording for a guide's routes and milestones (docs/TRD.md §7.21.8), shared by the page and the edit forms.

export const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

/** "—", "2019", "~2019", "Nov 2026", "17 Nov 2026". */
export function milestoneDate(m: Pick<Milestone, 'year' | 'month' | 'day' | 'approximate'>): string {
  if (m.year === null) return '—'
  if (m.month === null) return `${m.approximate ? '~' : ''}${m.year}`
  return `${m.day !== null ? `${m.day} ` : ''}${MONTHS[m.month - 1]} ${m.year}`
}

/** "Sep 2026", "2025", "Most recent trek" or "Not given". */
export function lastLed(r: { last_led_year: number | null; last_led_month: number | null; most_recent: boolean }): string {
  if (r.last_led_year !== null) return r.last_led_month !== null ? `${MONTHS[r.last_led_month - 1]} ${r.last_led_year}` : `${r.last_led_year}`
  return r.most_recent ? 'Most recent trek' : 'Not given'
}

/** "Any time", "Any time (Mar–Oct)", "From 15 Nov 2026", "Not open yet", "Ask Shray". */
export function availabilityLabel(r: RoutePlan, first: string): string {
  switch (r.availability) {
    case 'ANY_TIME':
      return r.season_from_month !== null && r.season_to_month !== null
        ? `Any time (${MONTHS[r.season_from_month - 1]}–${MONTHS[r.season_to_month - 1]})`
        : 'Any time'
    case 'FROM_DATE':
      return r.available_from ? `From ${isoDay(r.available_from)}` : `Ask ${first}`
    case 'NOT_OPEN':
      return 'Not open yet'
    case 'ASK':
      return `Ask ${first}`
  }
}

/** "2026-11-15" → "15 Nov 2026". */
function isoDay(iso: string): string {
  const [y, m, d] = iso.split('-').map(Number)
  return `${d} ${MONTHS[m - 1]} ${y}`
}

/** Just the plan part of a route, as the guide's and admin's forms send it. */
export const planOf = (r: RoutePlan): RoutePlan => ({
  availability: r.availability,
  season_from_month: r.season_from_month,
  season_to_month: r.season_to_month,
  available_from: r.available_from,
  note: r.note,
})

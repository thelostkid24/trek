// Display helpers shared by listings, bookings and admin screens.

const rupeeFormat = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 })
const rupeeFormatExact = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' })

/** ₹2,199 — whole rupees unless there are paise. */
export function rupees(paise: number): string {
  return paise % 100 === 0 ? rupeeFormat.format(paise / 100) : rupeeFormatExact.format(paise / 100)
}

/** Trek dates are calendar dates (YYYY-MM-DD); parse them as local dates so the day never shifts. */
export function parseDate(iso: string): Date {
  const [y, m, d] = iso.split('-').map(Number)
  return new Date(y, m - 1, d)
}

export const dayLabel = (iso: string) =>
  parseDate(iso).toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric', month: 'short' })

export const longDate = (iso: string) =>
  parseDate(iso).toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })

/** "YYYY-MM" key and its short label ("Oct"). */
export const monthKey = (iso: string) => iso.slice(0, 7)
export const monthLabel = (key: string) =>
  parseDate(`${key}-01`).toLocaleDateString('en-IN', { month: 'short', year: 'numeric' })

/** "Sat 24 Oct" or "Sat 24 – Sun 25 Oct". */
export function dateRange(start: string, end: string): string {
  if (start === end) return dayLabel(start)
  return `${dayLabel(start)} – ${dayLabel(end)}`
}

/** Compact trek dates for departure rows: "19–24 Dec", or "26 Dec – 1 Jan" across months. */
export function shortRange(start: string, end: string): string {
  const a = parseDate(start)
  const b = parseDate(end)
  const month = (d: Date) => d.toLocaleDateString('en-IN', { month: 'short' })
  if (start === end) return `${a.getDate()} ${month(a)}`
  if (a.getMonth() === b.getMonth()) return `${a.getDate()}–${b.getDate()} ${month(b)}`
  return `${a.getDate()} ${month(a)} – ${b.getDate()} ${month(b)}`
}

/** The weekdays and year under a short range: "Sat–Thu · 2026". */
export function weekdaysAndYear(start: string, end: string): string {
  const weekday = (iso: string) => parseDate(iso).toLocaleDateString('en-IN', { weekday: 'short' })
  const days = start === end ? weekday(start) : `${weekday(start)}–${weekday(end)}`
  return `${days} · ${parseDate(start).getFullYear()}`
}

/**
 * Altitudes are stored in metres and shown in feet, rounded to 5 ft so values typed in feet round-trip
 * (12,500 ft → 3,810 m → 12,500 ft).
 */
export const toFeet = (m: number) => Math.round((m * 3.28084) / 5) * 5
export const toMetres = (ft: number) => Math.round(ft / 3.28084)

/** "12,500 ft". */
export const feet = (m: number) => `${toFeet(m).toLocaleString('en-IN')} ft`

export const dateTime = (iso: string) =>
  new Date(iso).toLocaleString('en-IN', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' })

/** Today's date (YYYY-MM-DD) on the Indian calendar, which the backend uses for trek dates. */
export function todayIst(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Kolkata' }).format(new Date())
}

/** "+919812345678" → "+91 98123 45678". Other numbers are returned as-is. */
export function formatPhone(e164: string): string {
  const m = /^\+91(\d{5})(\d{5})$/.exec(e164)
  return m ? `+91 ${m[1]} ${m[2]}` : e164
}

/** "+919812345678" → "+91 ••••• ••678", for places where the full number isn't needed. */
export function maskPhone(e164: string): string {
  const m = /^\+91\d{7}(\d{3})$/.exec(e164)
  return m ? `+91 ••••• ••${m[1]}` : e164
}

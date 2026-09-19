import type { AuthResponse } from './auth'
import type { DepartureStatus, GuideBrief, Items, TrackBrief } from './catalog'
import { apiFetch } from './client'
import type { Payment } from './payments'
import type { Gender } from './profile'
import type { CancelReason } from './admin'

// Contract: docs/TRD.md §7.6. Call through `withAuth` from useAuth(), except the public guest checkout.

export type BookingStatus =
  | 'HELD'
  | 'CONFIRMED'
  | 'EXPIRED'
  | 'RELEASED'
  | 'CANCELLED_BY_TREKKER'
  | 'CANCELLED_FORCE_MAJEURE'

export const BOOKING_STATUS_LABEL: Record<BookingStatus, string> = {
  HELD: 'Awaiting payment',
  CONFIRMED: 'Confirmed',
  EXPIRED: 'Hold expired',
  RELEASED: 'Seats released',
  CANCELLED_BY_TREKKER: 'Cancelled',
  CANCELLED_FORCE_MAJEURE: 'Cancelled — full refund',
}

export type Traveller = {
  full_name: string
  phone: string | null
  date_of_birth: string
  gender: Gender
}

export type RefundStatus = 'PENDING' | 'PROCESSED' | 'FAILED'
export type RefundKind = 'TREKKER_CANCELLATION' | 'FORCE_MAJEURE' | 'LATE_CAPTURE'

export type Refund = {
  id: string
  amount_paise: number
  status: RefundStatus
  kind: RefundKind
  created_at: string
}

/** Who we reach about the booking; `phone` is the WhatsApp number. */
export type BookingContact = { full_name: string | null; phone: string | null; email: string | null }

export type RefundTier = { min_days_before: number; refund_bps: number }

export type Booking = {
  id: string
  status: BookingStatus
  seats: number
  price_paise_per_seat: number
  amount_paise: number
  contact: BookingContact
  /** Every seat has a named traveller. Travellers can be added after payment. */
  travellers_complete: boolean
  hold_expires_at: string
  confirmed_at: string | null
  cancelled_at: string | null
  departure: {
    id: string
    status: DepartureStatus
    start_date: string
    end_date: string
    meeting_point: string
    cancel_reason_code: CancelReason | null
    cancel_reason_note: string | null
    track: TrackBrief
    guide: GuideBrief
  }
  travellers: Traveller[]
  payment: Payment | null
  refunds: Refund[]
  refund_policy: RefundTier[] | null
  created_at: string
}

export type TravellerInput = { full_name: string; phone: string | null; date_of_birth: string; gender: Gender }

/** Signed in: contact fields left out come from the account. Travellers can wait until after payment. */
export type NewBooking = {
  departure_id: string
  seats: number
  full_name?: string
  phone?: string
  email?: string
  travellers?: TravellerInput[]
}

/** Guest checkout: three fields, no password, no OTP. `phone` is the WhatsApp number (+91…). */
export type NewGuestBooking = {
  departure_id: string
  seats: number
  full_name: string
  phone: string
  email: string
}

export type GuestBookingResult = { booking: Booking; auth: AuthResponse }

export type CancellationQuote = {
  allowed: boolean
  days_before_start: number
  refund_bps: number
  refund_paise: number
}

export const createBooking = (token: string, body: NewBooking) =>
  apiFetch<Booking>('/api/trekker/bookings', { method: 'POST', token, body })

/** Public. Signs the guest in (refresh cookie + `auth`); never call it while signed in. */
export const createGuestBooking = (body: NewGuestBooking) =>
  apiFetch<GuestBookingResult>('/api/public/bookings', { method: 'POST', body, credentials: 'include' })

export const updateTravellers = (token: string, id: string, travellers: TravellerInput[]) =>
  apiFetch<Booking>(`/api/trekker/bookings/${id}/travellers`, { method: 'PUT', token, body: { travellers } })

export const listBookings = (token: string) => apiFetch<Items<Booking>>('/api/trekker/bookings', { token })

export const getBooking = (token: string, id: string) =>
  apiFetch<Booking>(`/api/trekker/bookings/${encodeURIComponent(id)}`, { token })

export const releaseHold = (token: string, id: string) =>
  apiFetch<Booking>(`/api/trekker/bookings/${id}/hold`, { method: 'DELETE', token })

export const getCancellationQuote = (token: string, id: string) =>
  apiFetch<CancellationQuote>(`/api/trekker/bookings/${id}/cancellation-quote`, { token })

export const cancelBooking = (token: string, id: string) =>
  apiFetch<Booking>(`/api/trekker/bookings/${id}/cancel`, { method: 'POST', token })

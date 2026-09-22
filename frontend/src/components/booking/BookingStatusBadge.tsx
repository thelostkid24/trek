import { BOOKING_STATUS_LABEL, type BookingStatus } from '../../api/bookings.ts'

const TONE: Record<BookingStatus, string> = {
  HELD: 'border-laterite-400/60 text-laterite-600',
  CONFIRMED: 'border-pine-600/40 text-pine-700',
  EXPIRED: 'border-paper-300 text-stone-500',
  RELEASED: 'border-paper-300 text-stone-500',
  CANCELLED_BY_TREKKER: 'border-paper-300 text-stone-500',
  CANCELLED_FORCE_MAJEURE: 'border-laterite-400/60 text-laterite-600',
}

/** Small outlined tag, as on the My treks card. */
export function BookingStatusBadge({ status }: { status: BookingStatus }) {
  return (
    <span className={`inline-flex shrink-0 rounded-(--field-radius) border px-2.5 py-1 text-xs ${TONE[status]}`}>
      {BOOKING_STATUS_LABEL[status]}
    </span>
  )
}

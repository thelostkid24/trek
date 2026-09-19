import { BOOKING_STATUS_LABEL, type BookingStatus } from '../../api/bookings.ts'

const TONE: Record<BookingStatus, string> = {
  HELD: 'bg-amber-100 text-amber-800',
  CONFIRMED: 'bg-brand-100 text-brand-800',
  EXPIRED: 'bg-stone-100 text-stone-600',
  RELEASED: 'bg-stone-100 text-stone-600',
  CANCELLED_BY_TREKKER: 'bg-stone-100 text-stone-600',
  CANCELLED_FORCE_MAJEURE: 'bg-laterite-100 text-laterite-600',
}

export function BookingStatusBadge({ status }: { status: BookingStatus }) {
  return (
    <span className={`inline-flex rounded-full px-2.5 py-0.5 text-xs font-medium ${TONE[status]}`}>
      {BOOKING_STATUS_LABEL[status]}
    </span>
  )
}

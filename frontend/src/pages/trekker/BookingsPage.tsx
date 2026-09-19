import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { listBookings, type Booking } from '../../api/bookings.ts'
import { messageFor } from '../../auth/errorMessages.ts'
import { useAuth } from '../../auth/useAuth.ts'
import { BookingStatusBadge } from '../../components/booking/BookingStatusBadge.tsx'
import { dateRange, rupees, todayIst } from '../../lib/format.ts'

/** /account/bookings — rendered inside <RequireAuth role="TREKKER">. */
export function BookingsPage() {
  const { withAuth } = useAuth()
  const bookings = useQuery({ queryKey: ['bookings'], queryFn: () => withAuth(listBookings) })
  const today = todayIst()
  const all = bookings.data?.items ?? []
  const isUpcoming = (b: Booking) =>
    (b.status === 'HELD' || b.status === 'CONFIRMED') && b.departure.end_date >= today
  const upcoming = all.filter(isUpcoming).sort((a, b) => a.departure.start_date.localeCompare(b.departure.start_date))
  const past = all.filter((b) => !isUpcoming(b))

  return (
    <div className="mx-auto max-w-3xl px-4 py-6 sm:py-10">
      <h1 className="font-display text-2xl font-semibold">My trips</h1>
      {bookings.isPending ? (
        <div className="mt-6 h-40 animate-pulse rounded-2xl bg-stone-100" aria-busy="true" aria-label="Loading trips" />
      ) : bookings.isError ? (
        <p className="mt-6 rounded-2xl border border-stone-200 bg-white p-6 text-center text-stone-700">
          {messageFor(bookings.error)}
        </p>
      ) : all.length === 0 ? (
        <div className="mt-6 rounded-2xl border border-stone-200 bg-white p-8 text-center">
          <p className="font-display text-lg font-semibold">No trips yet</p>
          <p className="mt-1 text-sm text-stone-600">Pick a departure and your seats are one step away.</p>
          <Link
            to="/#treks"
            className="mt-4 inline-block rounded-full bg-brand-900 px-5 py-2 text-sm font-medium text-white hover:bg-brand-800"
          >
            Find a departure
          </Link>
        </div>
      ) : (
        <>
          <TripList title="Upcoming" bookings={upcoming} empty="Nothing coming up." />
          {past.length > 0 && <TripList title="Past & cancelled" bookings={past} />}
        </>
      )}
    </div>
  )
}

function TripList({ title, bookings, empty }: { title: string; bookings: Booking[]; empty?: string }) {
  return (
    <section className="mt-6">
      <h2 className="text-sm font-semibold tracking-wide text-stone-500 uppercase">{title}</h2>
      {bookings.length === 0 ? (
        <p className="mt-2 text-sm text-stone-600">{empty}</p>
      ) : (
        <ul className="mt-3 space-y-3">
          {bookings.map((b) => (
            <li key={b.id}>
              <Link
                to={`/account/bookings/${b.id}`}
                className="flex items-center justify-between gap-3 rounded-2xl border border-stone-200 bg-white p-4 hover:border-brand-300"
              >
                <div className="min-w-0">
                  <p className="truncate font-display text-lg font-semibold">{b.departure.track.name}</p>
                  <p className="text-sm text-stone-600">
                    {dateRange(b.departure.start_date, b.departure.end_date)} · {b.seats}{' '}
                    {b.seats === 1 ? 'seat' : 'seats'} · {rupees(b.amount_paise)}
                  </p>
                </div>
                <BookingStatusBadge status={b.status} />
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

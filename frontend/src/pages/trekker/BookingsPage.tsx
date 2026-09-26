import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { BOOKING_STATUS_LABEL, listBookings, type Booking } from '../../api/bookings.ts'
import { messageFor } from '../../auth/errorMessages.ts'
import { useAuth } from '../../auth/useAuth.ts'
import { BookingStatusBadge } from '../../components/booking/BookingStatusBadge.tsx'
import { MoreMenu } from '../../components/booking/MoreMenu.tsx'
import { ComingSoon, Fact, PaperCard, TrekPicker } from '../../components/trips/TripPieces.tsx'
import { dayLabel, shortRange } from '../../lib/format.ts'
import { isCompleted, monthYear, splitTrips } from '../../lib/trips.ts'

/** /account/bookings ("My treks") — rendered inside <RequireAuth role="TREKKER">. */
export function BookingsPage() {
  const { withAuth } = useAuth()
  const bookings = useQuery({ queryKey: ['bookings'], queryFn: () => withAuth(listBookings) })
  const [selectedId, setSelectedId] = useState<string>()
  const all = bookings.data?.items ?? []
  const { upcoming, past } = splitTrips(all)
  const selected = upcoming.find((b) => b.id === selectedId) ?? upcoming[0]

  return (
    <div className="max-w-6xl px-5 py-8 sm:px-10 sm:py-10">
      <h1 className="sr-only">My treks</h1>
      {bookings.isPending ? (
        <div className="h-72 animate-pulse rounded-(--card-radius) bg-paper-200" aria-busy="true" aria-label="Loading treks" />
      ) : bookings.isError ? (
        <p className="text-stone-700">{messageFor(bookings.error)}</p>
      ) : all.length === 0 ? (
        <div className="rounded-(--card-radius) border border-paper-300 bg-paper-50 p-8 text-center">
          <p className="font-display text-xl text-stone-900">No treks yet</p>
          <p className="mt-1 text-sm text-stone-600">Pick a departure and your seats are one step away.</p>
          <Link
            to="/treks"
            className="mt-4 inline-block rounded-full bg-pine-600 px-5 py-2.5 text-sm font-medium text-white hover:bg-pine-700"
          >
            Find a departure
          </Link>
        </div>
      ) : (
        <>
          <section>
            <h2 className="font-display text-lg text-stone-700">Upcoming · {upcoming.length}</h2>
            {selected ? (
              <>
                <div className="mt-3">
                  <TrekPicker
                    items={upcoming}
                    selectedId={selected.id}
                    onSelect={setSelectedId}
                    label={(b) => b.departure.track.name}
                    sub={(b) =>
                      `${shortRange(b.departure.start_date, b.departure.end_date)} ${b.departure.start_date.slice(0, 4)} · ${b.departure.track.duration_days} days`
                    }
                  />
                </div>
                <UpcomingTrek booking={selected} />
              </>
            ) : (
              <p className="mt-2 text-sm text-stone-600">Nothing coming up.</p>
            )}
          </section>

          {past.length > 0 && (
            <section className="mt-10">
              <h2 className="border-b border-paper-300 pb-2 font-display text-lg text-stone-700">Past · {past.length}</h2>
              <ul>
                {past.map((b) => (
                  <li key={b.id}>
                    <Link
                      to={`/account/bookings/${b.id}`}
                      className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1 border-b border-paper-300 py-4 hover:text-pine-700"
                    >
                      <span className="font-display text-xl">{b.departure.track.name}</span>
                      <span className="text-xs text-stone-500">{pastLine(b)}</span>
                    </Link>
                  </li>
                ))}
              </ul>
            </section>
          )}
        </>
      )}
    </div>
  )
}

function pastLine(b: Booking): string {
  const guide = b.departure.guide.full_name ? ` · with ${b.departure.guide.full_name}` : ''
  if (isCompleted(b)) return `Completed ${monthYear(b.departure.end_date)}${guide}`
  return `${BOOKING_STATUS_LABEL[b.status]} · ${monthYear(b.departure.start_date)}`
}

function UpcomingTrek({ booking: b }: { booking: Booking }) {
  const d = b.departure
  const detail = `/account/bookings/${b.id}`

  return (
    <article className="mt-4 rounded-(--card-radius) border border-paper-300 bg-paper-50">
      <div className="flex items-start justify-between gap-3 p-6">
        <div>
          <h3 className="font-display text-2xl text-stone-900">{d.track.name}</h3>
          <p className="mt-1 text-sm text-stone-600">
            {shortRange(d.start_date, d.end_date)} {d.start_date.slice(0, 4)} · Booking {b.id.slice(0, 8).toUpperCase()}
          </p>
        </div>
        <BookingStatusBadge status={b.status} />
      </div>

      <div className="grid grid-cols-2 border-y border-paper-300 sm:grid-cols-4 [&>*]:p-5 [&>*:not(:last-child)]:border-paper-300 sm:[&>*:not(:last-child)]:border-r">
        <Fact label="Departure">{dayLabel(d.start_date)}</Fact>
        <Fact label="Guide">{d.guide.full_name ?? 'To be announced'}</Fact>
        <Fact label="Meeting point">{d.meeting_point}</Fact>
        <Fact label="Seats">{b.seats} booked</Fact>
      </div>

      <div className="flex flex-wrap items-center justify-between gap-4 p-5">
        <div className="flex flex-wrap items-center gap-x-5 gap-y-1 text-sm">
          <span className="flex items-center gap-2 text-pine-700/70">
            Itinerary <ComingSoon />
          </span>
          <span className={b.travellers_complete ? 'text-stone-700' : 'text-laterite-600'}>
            {b.travellers_complete ? 'Traveller details complete' : 'Traveller details needed'}
          </span>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Link to={detail} className="rounded-full bg-pine-600 px-4 py-2 text-sm font-medium text-white hover:bg-pine-700">
            {b.status === 'HELD' ? 'Complete payment' : 'Manage booking'}
          </Link>
          <Link
            to={`/account/gear?booking=${b.id}`}
            className="rounded-full border border-pine-600/60 bg-paper-50 px-4 py-2 text-sm font-medium text-pine-700 hover:border-pine-600"
          >
            Rent gear
          </Link>
          {b.status === 'CONFIRMED' && (
            <MoreMenu label="More options for this booking" items={[{ label: 'Request cancellation', to: `${detail}?cancel=1` }]} />
          )}
        </div>
      </div>

      <TripArrangements />
    </article>
  )
}

/** Add-ons, food, medical documents and coordinator have no backend yet (docs/TRD.md §7.10): laid out as designed, controls disabled. */
function TripArrangements() {
  const pill = 'rounded-(--field-radius) border px-3 py-1.5 text-sm disabled:cursor-not-allowed'
  return (
    <div className="border-t border-paper-300 bg-paper-100/60 p-5 sm:p-6">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h4 className="flex items-center gap-3 font-display text-lg text-stone-900">
          Trip arrangements <ComingSoon />
        </h4>
        <p className="text-xs text-stone-500">Add-ons close 7 days before departure · food locks 4 days before</p>
      </div>
      <div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <PaperCard>
          <p className="text-xs text-stone-500">Add-ons</p>
          {[
            { name: 'Offload', note: 'Porter carries your main bag' },
            { name: 'Transport', note: 'Shared cab to the base village and back' },
          ].map((addOn, i) => (
            <div key={addOn.name} className={`flex items-start justify-between gap-3 ${i ? 'mt-3 border-t border-paper-300 pt-3' : 'mt-2'}`}>
              <div>
                <p className="text-sm text-stone-900">{addOn.name}</p>
                <p className="text-xs text-stone-500">{addOn.note}</p>
              </div>
              <button type="button" disabled className={`${pill} shrink-0 border-pine-600/40 px-2.5 py-1 text-xs text-pine-700/70`}>
                Request
              </button>
            </div>
          ))}
        </PaperCard>
        <PaperCard>
          <p className="text-xs text-stone-500">Food option</p>
          <div className="mt-2 flex gap-2">
            <button type="button" disabled className={`${pill} border-paper-300 text-stone-600`}>
              Veg
            </button>
            <button type="button" disabled className={`${pill} border-paper-300 text-stone-600`}>
              Veg + egg
            </button>
          </div>
          <p className="mt-3 text-xs leading-relaxed text-stone-500">
            Non-veg food isn't served on the trail and the menu is fixed for the whole batch. Preferences lock 4 days before
            departure so the kitchen can buy locally.
          </p>
        </PaperCard>
        <PaperCard>
          <p className="text-xs text-stone-500">Medical documents</p>
          <div className="mt-2 flex items-center gap-3 rounded-(--field-radius) bg-paper-200/70 p-3">
            <span className="size-7 shrink-0 rounded-sm border border-paper-300 bg-paper-200" aria-hidden="true" />
            <p className="text-sm text-stone-700">No file uploaded</p>
          </div>
          <button type="button" disabled className={`${pill} mt-3 border-paper-300 text-stone-600`}>
            Upload certificate
          </button>
          <p className="mt-3 text-xs leading-relaxed text-stone-500">Certificate is due 10 days before your trek date.</p>
        </PaperCard>
        <div className="rounded-(--card-radius) bg-ink-800 p-4 text-sm">
          <p className="text-xs text-ink-400">Coordinator</p>
          <p className="mt-2 text-white/70">Your trip coordinator's name, phone and email will appear here.</p>
        </div>
      </div>
    </div>
  )
}

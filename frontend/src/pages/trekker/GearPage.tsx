import { useQuery } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router-dom'
import { listBookings } from '../../api/bookings.ts'
import { messageFor } from '../../auth/errorMessages.ts'
import { useAuth } from '../../auth/useAuth.ts'
import { PaperCard, TrekPicker } from '../../components/trips/TripPieces.tsx'
import { shortRange } from '../../lib/format.ts'
import { splitTrips } from '../../lib/trips.ts'

/** What vendors at base camp will rent out. Placeholder until gear rental is built (docs/TRD.md §7.10). */
const GEAR = [
  { name: 'Down jacket', note: '−15°C rated, vendor-supplied' },
  { name: 'Trekking poles', note: 'Pair, collapsible aluminium' },
  { name: 'Sleeping bag', note: 'Liner included, washed between treks' },
  { name: 'Gaiters', note: 'For snow on the upper trail' },
  { name: 'Headlamp', note: 'Batteries included' },
  { name: 'Micro-spikes', note: 'Summit-day traction, sizes 5–12' },
]

/** /account/gear — rendered inside <RequireAuth role="TREKKER">. `?booking=` picks the trek. */
export function GearPage() {
  const { withAuth } = useAuth()
  const [params, setParams] = useSearchParams()
  const bookings = useQuery({ queryKey: ['bookings'], queryFn: () => withAuth(listBookings) })
  const upcoming = splitTrips(bookings.data?.items ?? []).upcoming
  const selected = upcoming.find((b) => b.id === params.get('booking')) ?? upcoming[0]

  return (
    <div className="max-w-5xl px-5 py-8 sm:px-10 sm:py-10">
      <h1 className="sr-only">Gear</h1>

      {bookings.isPending ? (
        <div className="h-40 animate-pulse rounded-(--card-radius) bg-paper-200" aria-busy="true" aria-label="Loading treks" />
      ) : bookings.isError ? (
        <p className="text-stone-700">{messageFor(bookings.error)}</p>
      ) : upcoming.length === 0 ? (
        <p className="text-sm text-stone-600">
          Rentals are booked for a particular trek.{' '}
          <Link to="/treks" className="text-pine-700 underline">
            Find a departure
          </Link>{' '}
          first.
        </p>
      ) : (
        <div>
          <p className="text-xs text-stone-500">Rentals are booked separately for each trek. Choose which one you're renting for:</p>
          <div className="mt-2">
            <TrekPicker
              items={upcoming}
              selectedId={selected?.id}
              onSelect={(id) => setParams({ booking: id }, { replace: true })}
              label={(b) => b.departure.track.name}
              sub={(b) =>
                `${shortRange(b.departure.start_date, b.departure.end_date)} ${b.departure.start_date.slice(0, 4)} · ${b.departure.track.duration_days} days`
              }
            />
          </div>
        </div>
      )}

      <div className="mt-8 grid gap-6 lg:grid-cols-[1fr_18rem] lg:items-start">
        <ul className="grid gap-4 sm:grid-cols-2">
          {GEAR.map((item) => (
            <li key={item.name} className="overflow-hidden rounded-(--card-radius) border border-paper-300 bg-paper-50">
              <div className="flex aspect-[4/3] items-center justify-center bg-paper-200 text-xs text-stone-400">Photo coming soon</div>
              <div className="p-4">
                <p className="text-sm font-medium text-stone-900">{item.name}</p>
                <p className="text-xs text-stone-500">{item.note}</p>
                <p className="mt-2 text-xs text-stone-500">Per-day rate coming soon</p>
                <button
                  type="button"
                  disabled
                  className="mt-3 w-full cursor-not-allowed rounded-full border border-paper-300 py-2 text-sm text-stone-400"
                >
                  Add to rental
                </button>
              </div>
            </li>
          ))}
        </ul>

        <PaperCard className="lg:sticky lg:top-20">
          <p className="font-display text-lg text-stone-900">Your rental</p>
          {selected && (
            <p className="text-xs text-stone-500">
              {selected.departure.track.name} · {selected.departure.track.duration_days} days
            </p>
          )}
          <p className="mt-4 border-t border-paper-300 pt-4 text-sm text-stone-600">Nothing selected yet.</p>
          <button
            type="button"
            disabled
            className="mt-4 w-full cursor-not-allowed rounded-full bg-paper-300 py-2.5 text-sm text-stone-500"
          >
            Pay &amp; confirm rental
          </button>
          <p className="mt-3 text-xs text-stone-500">Paid items are confirmed with the vendor and delivered to base camp.</p>
        </PaperCard>
      </div>

      <p className="mt-6 max-w-md text-xs text-stone-500">
        Sizes are confirmed with your guide before departure. Items are collected back at base camp on the last day.
      </p>
    </div>
  )
}

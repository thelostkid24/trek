import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { getDeparture, type DepartureDetail } from '../api/catalog.ts'
import { ApiError } from '../api/client.ts'
import { messageFor } from '../auth/errorMessages.ts'
import { DifficultyPill, SeatMeter } from '../components/catalog/DeparturePieces.tsx'
import { OtherDepartures } from '../components/catalog/OtherDepartures.tsx'
import { GuideLine, Itinerary, TrekFacts } from '../components/catalog/TrekPieces.tsx'
import { trekTagline } from '../lib/trek.ts'
import { Ridgeline } from '../components/Ridgeline.tsx'
import { dateRange, longDate, rupees } from '../lib/format.ts'

/** /departures/:id — public. */
export function DepartureDetailPage() {
  const { id = '' } = useParams()
  const departure = useQuery({
    queryKey: ['public-departure', id],
    queryFn: () => getDeparture(id),
    retry: (count, error) => !(error instanceof ApiError && error.status === 404) && count < 2,
  })

  if (departure.isPending) {
    return (
      <div className="mx-auto max-w-5xl space-y-4 px-4 py-10" aria-busy="true" aria-label="Loading departure">
        <div className="h-48 animate-pulse rounded-2xl bg-stone-200" />
        <div className="h-64 animate-pulse rounded-2xl bg-stone-100" />
      </div>
    )
  }
  if (departure.isError) {
    const missing = departure.error instanceof ApiError && departure.error.status === 404
    return (
      <section className="mx-auto max-w-md px-4 py-16 text-center">
        <h1 className="font-display text-2xl font-semibold">
          {missing ? 'This departure is not available' : 'Something went wrong'}
        </h1>
        <p className="mt-2 text-stone-600">{missing ? 'It may have been removed.' : messageFor(departure.error)}</p>
        <Link to="/#treks" className="mt-4 inline-block text-brand-700 underline">
          See all treks
        </Link>
      </section>
    )
  }
  return <Detail departure={departure.data} />
}

function Detail({ departure: d }: { departure: DepartureDetail }) {
  const { track, guide } = d
  return (
    <>
      <section className="relative isolate overflow-hidden bg-brand-950 text-white">
        <Ridgeline className="absolute inset-0 -z-20 h-full w-full" />
        <div className="absolute inset-0 -z-10 bg-gradient-to-r from-brand-950/90 via-brand-950/60 to-brand-950/20" />
        <div className="mx-auto max-w-5xl px-4 pt-10 pb-12 sm:pt-16 sm:pb-20">
          <Link to={`/treks/${track.slug}`} viewTransition className="text-sm text-brand-200 hover:text-white">
            ← All {track.name} dates
          </Link>
          <div className="mt-4 flex flex-wrap items-center gap-2">
            <DifficultyPill difficulty={track.difficulty} />
            <span className="text-sm text-brand-200">
              {track.region} · {track.duration_days} {track.duration_days === 1 ? 'day' : 'days'}
              {track.max_altitude_m && <> · {track.max_altitude_m.toLocaleString('en-IN')} m</>}
            </span>
          </div>
          <h1 className="mt-3 font-display text-4xl leading-tight font-semibold tracking-tight sm:text-5xl">
            {track.name}
          </h1>
          <p className="mt-3 max-w-2xl text-lg text-brand-100">{track.summary}</p>
        </div>
      </section>

      <div className="mx-auto grid max-w-5xl gap-6 px-4 py-8 sm:py-12 lg:grid-cols-[1fr_320px] lg:items-start">
        <div className="space-y-6">
          {/* The guide comes first: who you walk with is the decision. */}
          <section className="rounded-2xl border border-stone-200 bg-white p-5 sm:p-7">
            <p className="text-xs font-semibold tracking-[0.18em] text-laterite-600 uppercase">Your guide</p>
            <div className="mt-3 flex flex-wrap items-center justify-between gap-4">
              <GuideLine guide={guide} trekName={track.name} size="md" />
              <Link
                to={`/guides/${guide.id}`}
                className="rounded-full bg-white px-4 py-2 text-sm font-medium text-stone-700 ring-1 ring-stone-300 hover:ring-stone-400"
              >
                View profile
              </Link>
            </div>
            <p className="mt-3 text-sm text-stone-600">Local, vetted, and leading no more than {d.max_group_size}.</p>
          </section>

          <section className="space-y-6 rounded-2xl border border-stone-200 bg-white p-5 sm:p-7">
            <div>
              <h2 className="font-display text-xl font-semibold">About the trek</h2>
              <p className="mt-1 text-sm text-stone-500">{trekTagline(track)}</p>
              <p className="mt-3 whitespace-pre-line text-stone-700">{track.description}</p>
            </div>
            <TrekFacts track={track} />
            <Itinerary track={track} />
            <dl className="grid gap-4 text-sm sm:grid-cols-2">
              <div>
                <dt className="text-stone-500">Dates</dt>
                <dd className="mt-0.5 font-medium text-stone-900">
                  {d.start_date === d.end_date ? longDate(d.start_date) : dateRange(d.start_date, d.end_date)}
                </dd>
              </div>
              <div>
                <dt className="text-stone-500">Meeting point</dt>
                <dd className="mt-0.5 font-medium text-stone-900">{track.meeting_point}</dd>
              </div>
            </dl>
          </section>

          <OtherDepartures current={d} title={`Other ${track.name} dates`} />
        </div>

        <aside className="rounded-2xl border border-stone-200 bg-white p-5 sm:p-6 lg:sticky lg:top-20">
          <p>
            <span className="text-2xl font-semibold">{rupees(d.price_paise)}</span>
            <span className="text-sm text-stone-500"> / person</span>
          </p>
          <p className="mt-1 text-sm text-stone-600">{dateRange(d.start_date, d.end_date)}</p>
          <div className="mt-5">
            <SeatMeter size={d.max_group_size} left={d.seats_left} />
          </div>
          <div className="mt-5 border-t border-stone-100 pt-4">
            <GuideLine guide={guide} trekName={track.name} />
          </div>
          <BookingCta departure={d} />
          <ul className="mt-5 space-y-1.5 text-xs text-stone-600">
            <li>✓ {rupees(d.price_paise)} per person is the full price</li>
            <li>✓ No account needed — name, WhatsApp and email</li>
            <li>✓ Full refund if weather, permits or safety stop the trek</li>
          </ul>
        </aside>
      </div>
    </>
  )
}

function BookingCta({ departure: d }: { departure: DepartureDetail }) {
  if (d.status !== 'PUBLISHED') {
    const label = { CANCELLED: 'This departure was cancelled', EXPIRED: 'This departure has closed', COMPLETED: 'This trek has taken place' }[d.status]
    return <p className="mt-5 rounded-xl bg-stone-100 px-4 py-3 text-center text-sm font-medium text-stone-600">{label}</p>
  }
  if (!d.bookable) {
    return (
      <p className="mt-5 rounded-xl bg-stone-100 px-4 py-3 text-center text-sm font-medium text-stone-600">
        {d.seats_left === 0 ? 'This batch is full' : 'Bookings for this departure have closed'}
      </p>
    )
  }
  // No sign-in: /book takes guests straight to the three-field checkout.
  return (
    <Link
      to={`/book/${d.id}`}
      className="mt-5 block rounded-full bg-laterite-500 px-6 py-3 text-center font-medium text-white shadow-lg shadow-laterite-600/20 hover:bg-laterite-600"
    >
      Book seats
    </Link>
  )
}

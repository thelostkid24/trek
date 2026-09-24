import { useQuery } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { Link, useParams } from 'react-router-dom'
import { getDeparture, trekQueryOptions, type DepartureDetail, type TrekPage } from '../api/catalog.ts'
import { ApiError } from '../api/client.ts'
import { messageFor } from '../auth/errorMessages.ts'
import { DayByDay } from '../components/catalog/DayByDay.tsx'
import { FillBar } from '../components/catalog/DeparturePieces.tsx'
import { GuideProfileCard } from '../components/catalog/GuideProfileCard.tsx'
import { OtherDepartures } from '../components/catalog/OtherDepartures.tsx'
import { Cancellation, FactGrid, Inclusions, TrekSection } from '../components/catalog/TrekSections.tsx'
import { Ridgeline } from '../components/Ridgeline.tsx'
import { rupees, shortRange, weekdaysAndYear } from '../lib/format.ts'

/**
 * /departures/:id — public. One dated run: how full it is, who leads it (above the Book button), the full price
 * and what it doesn't cover, the days with their dates, and the refund dates. Lists come from the trek page.
 */
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
  // The trek page carries the lists, refund tiers and charity; the departure page shows them for these dates.
  const trek = useQuery(trekQueryOptions(track.slug))
  const extras: TrekPage | undefined = trek.data
  const cover = track.photos[0]
  const guideName = guide.full_name ?? 'a local guide'

  return (
    <div className="bg-paper-50 pb-24 lg:pb-0">
      <section className="relative isolate h-44 overflow-hidden bg-brand-950 sm:h-64">
        {cover ? (
          <>
            <img src={cover.url} alt={cover.caption ?? ''} className="absolute inset-0 -z-20 size-full object-cover" />
            <div className="absolute inset-0 -z-10 bg-gradient-to-b from-black/30 via-transparent to-black/30" aria-hidden="true" />
          </>
        ) : (
          <Ridgeline className="absolute inset-0 -z-10 h-full w-full" />
        )}
      </section>

      <div className="mx-auto grid max-w-6xl gap-x-12 gap-y-8 px-4 py-8 sm:py-10 lg:grid-cols-[minmax(0,1fr)_22rem]">
        <header className="lg:col-start-1">
          <Link to={`/treks/${track.slug}`} viewTransition className="text-sm text-brand-700 hover:text-brand-900">
            ← All {track.name} dates
          </Link>
          <p className="mt-3 text-xs font-semibold tracking-[0.16em] text-laterite-600 uppercase">{track.name}</p>
          <h1 className="mt-1 font-serif text-4xl leading-tight tracking-tight text-stone-900 sm:text-6xl">
            {shortRange(d.start_date, d.end_date)} {d.start_date.slice(0, 4)}
          </h1>
          <p className="mt-2 text-stone-600">
            {weekdaysAndYear(d.start_date, d.end_date).split(' · ')[0]} · {track.duration_days}{' '}
            {track.duration_days === 1 ? 'day' : 'days'} · with {guideName}
          </p>
          <div className="mt-5 max-w-xl">
            <FillBar size={d.max_group_size} left={d.seats_left} bookable={d.bookable} />
          </div>
        </header>

        <aside className="hidden lg:sticky lg:top-20 lg:col-start-2 lg:row-span-2 lg:row-start-1 lg:block lg:self-start">
          <PriceCard departure={d} extras={extras} />
        </aside>

        <div className="min-w-0 space-y-8 lg:col-start-1">
          <GuideProfileCard guide={guide} trekName={track.name} />

          {/* Phones: the full price and Book right under the guide; the bar at the bottom keeps Book in reach. */}
          <div className="lg:hidden">
            <PriceCard departure={d} extras={extras} />
          </div>

          {extras && extras.content.INCLUDED.length + extras.content.NOT_INCLUDED.length > 0 && (
            <TrekSection id="included" label="What the price covers">
              <Inclusions included={extras.content.INCLUDED} excluded={extras.content.NOT_INCLUDED} open />
            </TrekSection>
          )}
          <TrekSection id="facts" label="The trek">
            <FactGrid track={track} />
          </TrekSection>
          <DayByDay id="days" days={track.itinerary} startDate={d.start_date} />
          {extras && extras.refund_tiers.length > 0 && (
            <TrekSection id="cancellation" label="If your plans change">
              <Cancellation tiers={extras.refund_tiers} startDate={d.start_date} />
            </TrekSection>
          )}
          <OtherDepartures current={d} title={`Other ${track.name} dates`} />
        </div>
      </div>

      {d.bookable && (
        <div className="fixed inset-x-0 bottom-0 z-20 flex items-center justify-between gap-4 border-t border-paper-300 bg-paper-50/95 px-4 py-3 backdrop-blur lg:hidden">
          <div>
            <p className="font-semibold text-stone-900">{rupees(d.price_paise)} <span className="text-sm font-normal text-stone-500">per person</span></p>
            <p className="text-xs text-stone-600">{d.seats_left} of {d.max_group_size} seats open</p>
          </div>
          <Link to={`/book/${d.id}`} className="rounded-lg bg-brand-900 px-5 py-3 font-semibold text-white hover:bg-brand-800">
            Book these dates
          </Link>
        </div>
      )}
    </div>
  )
}

/** The full price, what's extra, the charity share, then Book. */
function PriceCard({ departure: d, extras }: { departure: DepartureDetail; extras: TrekPage | undefined }) {
  const { track } = d
  return (
    <section aria-label="Price" className="rounded-2xl bg-white p-5 ring-1 ring-paper-300 sm:p-6">
      <p className="text-xs font-semibold tracking-[0.16em] text-laterite-600 uppercase">Full price</p>
      <p className="mt-2">
        <span className="font-serif text-4xl text-stone-900">{rupees(d.price_paise)}</span>
        <span className="text-stone-500"> per person</span>
      </p>
      <p className="mt-1 text-sm text-stone-600">
        {track.pickup_drop ? `${track.pickup_drop}, with everything` : 'Everything'} in “What the price covers”. Nothing is
        added at checkout.
      </p>
      <ul className="mt-4 space-y-2 border-t border-paper-200 pt-4 text-sm text-stone-700">
        {track.offloading && (
          <Line>
            Bag offloading is extra
            {track.offloading_price_paise ? `: ${rupees(track.offloading_price_paise)}` : ', priced separately'}
          </Line>
        )}
        <Line>No account needed: name, WhatsApp and email</Line>
        <Line>Full refund if weather, permits or safety stop the trek</Line>
        {extras?.charity && (
          <Line>
            {extras.charity.bps / 100}% goes to {extras.charity.name}, from the price, not on top
          </Line>
        )}
      </ul>
      <BookingCta departure={d} />
    </section>
  )
}

function Line({ children }: { children: ReactNode }) {
  return (
    <li className="flex gap-2">
      <span className="text-brand-700" aria-hidden="true">
        ✓
      </span>
      <span>{children}</span>
    </li>
  )
}

function BookingCta({ departure: d }: { departure: DepartureDetail }) {
  if (d.status !== 'PUBLISHED') {
    const label = { CANCELLED: 'This departure was cancelled', EXPIRED: 'This departure has closed', COMPLETED: 'This trek has taken place' }[d.status]
    return <p className="mt-5 rounded-xl bg-paper-100 px-4 py-3 text-center text-sm font-medium text-stone-600">{label}</p>
  }
  if (!d.bookable) {
    return (
      <p className="mt-5 rounded-xl bg-paper-100 px-4 py-3 text-center text-sm font-medium text-stone-600">
        {d.seats_left === 0 ? 'This batch is full' : 'Bookings for this departure have closed'}
      </p>
    )
  }
  // No sign-in: /book takes guests straight to the three-field checkout.
  return (
    <Link
      to={`/book/${d.id}`}
      className="mt-5 block rounded-lg bg-brand-900 px-6 py-3.5 text-center font-semibold text-white hover:bg-brand-800"
    >
      Book these dates
    </Link>
  )
}

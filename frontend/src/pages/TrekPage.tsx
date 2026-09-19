import { useQuery } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { Link, useParams } from 'react-router-dom'
import { DIFFICULTY_LABEL, MAX_GROUP_SIZE, getTrek, type TrekDeparture, type TrekPage as Trek } from '../api/catalog.ts'
import { ApiError } from '../api/client.ts'
import { messageFor } from '../auth/errorMessages.ts'
import { SeatMeter } from '../components/catalog/DeparturePieces.tsx'
import { GuideLine, Itinerary, TrekFacts } from '../components/catalog/TrekPieces.tsx'
import { trekTagline } from '../lib/trek.ts'
import { Ridgeline } from '../components/Ridgeline.tsx'
import { rupees, shortRange, weekdaysAndYear } from '../lib/format.ts'

/** /treks/:slug — public. One trek, every upcoming departure, each with its own guide. */
export function TrekPage() {
  const { slug = '' } = useParams()
  const trek = useQuery({
    queryKey: ['public-trek', slug],
    queryFn: () => getTrek(slug),
    retry: (count, error) => !(error instanceof ApiError && error.status === 404) && count < 2,
  })

  if (trek.isPending) {
    return (
      <div className="mx-auto max-w-6xl space-y-4 px-4 py-10" aria-busy="true" aria-label="Loading trek">
        <div className="h-56 animate-pulse rounded-2xl bg-stone-200" />
        <div className="h-72 animate-pulse rounded-2xl bg-stone-100" />
      </div>
    )
  }
  if (trek.isError) {
    const missing = trek.error instanceof ApiError && trek.error.status === 404
    return (
      <section className="mx-auto max-w-md px-4 py-16 text-center">
        <h1 className="font-display text-2xl font-semibold">{missing ? 'This trek is not available' : 'Something went wrong'}</h1>
        <p className="mt-2 text-stone-600">{missing ? 'It may have been renamed or removed.' : messageFor(trek.error)}</p>
        <Link to="/#treks" className="mt-4 inline-block text-brand-700 underline">
          See all treks
        </Link>
      </section>
    )
  }
  return <Page trek={trek.data} />
}

function Page({ trek }: { trek: Trek }) {
  const { track, departures } = trek
  const fromPrice = departures.length > 0 ? Math.min(...departures.map((d) => d.price_paise)) : null

  return (
    <>
      {/* No trek photos yet: the ridgeline stands in for the hero image. */}
      <section className="relative isolate h-56 overflow-hidden bg-brand-950 sm:h-72">
        <Ridgeline className="absolute inset-0 -z-10 h-full w-full" />
        <div className="mx-auto flex h-full max-w-6xl items-start px-4 pt-5">
          {track.season_label && (
            <span className="rounded-full bg-white/90 px-3 py-1 text-xs font-medium text-stone-800">{track.season_label}</span>
          )}
        </div>
      </section>

      <div className="mx-auto grid max-w-6xl gap-8 px-4 py-8 sm:py-10 lg:grid-cols-[1fr_360px] lg:items-start">
        <header className="lg:col-start-1">
          <Link to="/#treks" className="text-sm text-brand-700 hover:text-brand-900">
            ← All treks
          </Link>
          <h1 className="mt-2 font-display text-4xl leading-tight font-semibold tracking-tight sm:text-5xl">{track.name}</h1>
          <p className="mt-2 text-stone-600">{trekTagline(track)}</p>
          {/* Phones get the headline facts as chips; the cards below carry them on wider screens. */}
          <ul className="mt-4 flex flex-wrap gap-2 text-xs text-stone-700 lg:hidden">
            <Chip>{DIFFICULTY_LABEL[track.difficulty]}</Chip>
            {track.distance_km && <Chip>{track.distance_km} km</Chip>}
            <Chip>Micro-batch · {MAX_GROUP_SIZE} per guide</Chip>
            {fromPrice !== null && <Chip>from {rupees(fromPrice)}</Chip>}
          </ul>
        </header>

        <aside className="lg:sticky lg:top-20 lg:col-start-2 lg:row-span-2 lg:row-start-1">
          <h2 className="text-xs font-semibold tracking-[0.14em] text-stone-500 uppercase">Departures</h2>
          {departures.length === 0 ? (
            <p className="mt-3 rounded-2xl bg-white p-5 text-sm text-stone-600 ring-1 ring-stone-200">
              No dates are open right now. Check back soon.
            </p>
          ) : (
            <ul className="mt-3 space-y-3">
              {departures.map((d) => (
                <DepartureRow key={d.id} departure={d} trekName={track.name} />
              ))}
            </ul>
          )}
        </aside>

        <div className="space-y-8 lg:col-start-1">
          <p className="max-w-2xl whitespace-pre-line text-stone-700">{track.description}</p>
          <TrekFacts track={track} />
          <Itinerary track={track} />
          <p className="text-sm text-stone-600">
            <span className="font-medium text-stone-800">Meeting point:</span> {track.meeting_point}
          </p>
        </div>
      </div>
    </>
  )
}

/** Tapping the dates opens the departure; the guide opens their page; Book goes to checkout. */
function DepartureRow({ departure: d, trekName }: { departure: TrekDeparture; trekName: string }) {
  return (
    <li className="rounded-2xl bg-white p-4 ring-1 ring-stone-200 transition hover:ring-brand-300">
      <Link to={`/departures/${d.id}`} className="block">
        <div className="flex items-baseline justify-between gap-3">
          <span className="text-lg font-semibold text-stone-900">{shortRange(d.start_date, d.end_date)}</span>
          <span className="font-semibold">{rupees(d.price_paise)}</span>
        </div>
        <p className="text-xs text-stone-500">{weekdaysAndYear(d.start_date, d.end_date)}</p>
        <div className="mt-3">
          <SeatMeter size={d.max_group_size} left={d.seats_left} />
        </div>
      </Link>
      <div className="mt-3 flex items-center justify-between gap-3 border-t border-stone-100 pt-3">
        <GuideLine guide={d.guide} trekName={trekName} />
        {d.bookable ? (
          <Link
            to={`/book/${d.id}`}
            className="shrink-0 rounded-full bg-brand-900 px-4 py-2 text-sm font-medium text-white hover:bg-brand-800"
          >
            Book
          </Link>
        ) : (
          <span className="shrink-0 text-xs font-medium text-stone-500">{d.seats_left === 0 ? 'Full' : 'Closed'}</span>
        )}
      </div>
    </li>
  )
}

function Chip({ children }: { children: ReactNode }) {
  return <li className="rounded-full bg-white px-3 py-1 ring-1 ring-stone-300">{children}</li>
}

import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { listDepartures, type DepartureSummary } from '../../api/catalog.ts'
import { dateRange, parseDate, rupees } from '../../lib/format.ts'
import { Avatar } from '../Avatar.tsx'
import { SeatMeter } from './DeparturePieces.tsx'

const daysBetween = (a: string, b: string) => Math.abs(parseDate(a).getTime() - parseDate(b).getTime()) / 86_400_000

/**
 * Other bookable departures of the same trek: the same date with another guide first, then the nearest dates.
 * With `bookState`, rows go straight to booking and carry the form state along.
 */
export function OtherDepartures({
  current,
  title,
  seats = 1,
  bookState,
  limit = 4,
}: {
  current: { id: string; start_date: string; track: { slug: string } }
  title: string
  /** Rows that can fit this many seats are marked. */
  seats?: number
  bookState?: unknown
  limit?: number
}) {
  const departures = useQuery({ queryKey: ['public-departures'], queryFn: listDepartures })
  const options = (departures.data?.items ?? [])
    .filter((d) => d.track.slug === current.track.slug && d.id !== current.id && d.bookable)
    .sort((a, b) => daysBetween(a.start_date, current.start_date) - daysBetween(b.start_date, current.start_date))
    .slice(0, limit)

  if (options.length === 0) return null
  return (
    <section>
      <h2 className="text-xs font-semibold tracking-[0.14em] text-stone-500 uppercase">{title}</h2>
      <ul className="mt-3 space-y-3">
        {options.map((d) => (
          <Row key={d.id} departure={d} sameDate={d.start_date === current.start_date} seats={seats} bookState={bookState} />
        ))}
      </ul>
    </section>
  )
}

function Row({
  departure: d,
  sameDate,
  seats,
  bookState,
}: {
  departure: DepartureSummary
  sameDate: boolean
  seats: number
  bookState?: unknown
}) {
  const fits = d.seats_left >= seats
  const guide = d.guide.full_name
  return (
    <li className="rounded-xl bg-white p-4 ring-1 ring-stone-200">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <p className="font-semibold text-stone-900">
          {dateRange(d.start_date, d.end_date)}
          {sameDate && <span className="ml-2 text-xs font-medium text-laterite-600">same dates</span>}
        </p>
        <span className="font-semibold">{rupees(d.price_paise)}</span>
      </div>
      <div className="mt-3">
        <SeatMeter size={d.max_group_size} left={d.seats_left} />
      </div>
      <div className="mt-3 flex flex-wrap items-center justify-between gap-3">
        <Link to={`/guides/${d.guide.id}`} viewTransition className="flex items-center gap-2 text-sm text-stone-700 hover:text-brand-800">
          <Avatar url={d.guide.avatar_url} name={guide} />
          {guide ? `with ${guide}` : 'Local guide'}
        </Link>
        {bookState !== undefined ? (
          <Link
            to={`/book/${d.id}`}
            state={bookState}
            className={`rounded-full px-4 py-2 text-sm font-medium ${
              fits ? 'bg-brand-900 text-white hover:bg-brand-800' : 'bg-white text-stone-700 ring-1 ring-stone-300'
            }`}
          >
            {fits ? `Book ${seats === 1 ? 'a seat' : `${seats} seats`} here` : 'View'}
          </Link>
        ) : (
          <Link to={`/departures/${d.id}`} viewTransition className="text-sm font-medium text-brand-700 hover:text-brand-900">
            View
          </Link>
        )}
      </div>
    </li>
  )
}

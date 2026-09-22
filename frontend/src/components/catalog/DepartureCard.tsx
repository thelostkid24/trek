import { Link } from 'react-router-dom'
import type { DepartureSummary } from '../../api/catalog.ts'
import { dayLabel, rupees } from '../../lib/format.ts'
import { DifficultyPill, SeatMeter } from './DeparturePieces.tsx'

export function DepartureCard({ departure: d }: { departure: DepartureSummary }) {
  const days = d.track.duration_days
  return (
    <li className="group relative flex flex-col rounded-2xl bg-white p-5 ring-1 ring-stone-200 transition hover:-translate-y-0.5 hover:shadow-lg hover:shadow-brand-950/5">
      <div className="flex items-center justify-between text-xs">
        <span className="font-medium text-stone-500">{dayLabel(d.start_date)}</span>
        <DifficultyPill difficulty={d.track.difficulty} />
      </div>
      <h3 className="mt-3 font-display text-xl font-semibold leading-snug text-brand-950">
        {/* The whole card is clickable through this link's overlay. */}
        <Link to={`/departures/${d.id}`} className="after:absolute after:inset-0 after:rounded-2xl">
          {d.track.name}
        </Link>
      </h3>
      <p className="mt-1 text-sm text-stone-600">
        {d.track.region} · {days} {days === 1 ? 'day' : 'days'}
        {d.guide.full_name && <> · with {d.guide.full_name.split(' ')[0]}</>}
      </p>

      <div className="mt-5">
        <SeatMeter size={d.max_group_size} left={d.seats_left} />
      </div>

      <div className="mt-5 flex items-end justify-between border-t border-stone-100 pt-4">
        <div>
          <span className="text-lg font-semibold">{rupees(d.price_paise)}</span>
          <span className="text-xs text-stone-500"> / person</span>
        </div>
        <span
          className={`rounded-full px-4 py-2 text-sm font-medium ${
            d.bookable ? 'bg-brand-900 text-white group-hover:bg-brand-800' : 'bg-stone-200 text-stone-500'
          }`}
        >
          {d.bookable ? 'Reserve' : d.seats_left === 0 ? 'Batch full' : 'Closed'}
        </span>
      </div>
    </li>
  )
}

import { Link } from 'react-router-dom'
import type { GuideSectionProps } from './types.ts'

/** The owner's "Mark my availability" under the departures (§7.21.7). Visitors see nothing: availability is private. */
export function AvailabilityLink({ owner }: GuideSectionProps) {
  if (!owner) return null
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl bg-white px-4 py-3.5 ring-1 ring-paper-300">
      <p className="text-sm text-stone-600">Dates and prices are set with the Empty Valley team.</p>
      <Link to="/guide/availability" className="text-sm font-medium text-brand-800 hover:text-brand-950">
        Mark my availability →
      </Link>
    </div>
  )
}

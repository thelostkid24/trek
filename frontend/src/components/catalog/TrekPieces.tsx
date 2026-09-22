import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { DIFFICULTY_LABEL, type Difficulty, type GuideCard, type TrackDetail } from '../../api/catalog.ts'
import { metres } from '../../lib/format.ts'
import { Avatar } from '../Avatar.tsx'

const DIFFICULTY_NOTE: Record<Difficulty, string> = {
  EASY: 'no prior trekking needed',
  MODERATE: 'steady fitness needed',
  CHALLENGING: 'prior high-altitude trekking needed',
}

/** Route facts as cards. Facts an admin hasn't filled in are left out. */
export function TrekFacts({ track }: { track: TrackDetail }) {
  const gain =
    track.base_altitude_m && track.max_altitude_m
      ? `${track.base_altitude_m.toLocaleString('en-IN')} → ${metres(track.max_altitude_m)}`
      : null
  return (
    <dl className="grid grid-cols-2 gap-3 sm:grid-cols-3 xl:grid-cols-5">
      {track.distance_km && <Fact label="Trek distance" value={`${track.distance_km} km`} note="on foot, start to finish" />}
      <Fact label="Difficulty" value={DIFFICULTY_LABEL[track.difficulty]} note={DIFFICULTY_NOTE[track.difficulty]} />
      {gain && <Fact label="Altitude gain" value={gain} note="trailhead to summit" />}
      {track.highest_camp_m && <Fact label="Highest camp" value={metres(track.highest_camp_m)} note="where you sleep" />}
      {track.stay && <Fact label="Stay" value={track.stay} />}
    </dl>
  )
}

function Fact({ label, value, note }: { label: string; value: ReactNode; note?: string }) {
  return (
    <div className="rounded-xl bg-white p-3.5 ring-1 ring-stone-200">
      <dt className="text-[0.65rem] font-semibold tracking-[0.14em] text-stone-500 uppercase">{label}</dt>
      <dd className="mt-1 font-semibold text-stone-900">{value}</dd>
      {note && <dd className="mt-0.5 text-xs text-stone-500">{note}</dd>}
    </div>
  )
}

/** "The six days": one line per day, in two columns from sm up. */
export function Itinerary({ track }: { track: TrackDetail }) {
  if (track.itinerary.length === 0) return null
  const words = ['one', 'two', 'three', 'four', 'five', 'six', 'seven']
  const count = track.itinerary.length
  return (
    <section>
      <h2 className="text-xs font-semibold tracking-[0.14em] text-stone-500 uppercase">
        The {count === 1 ? 'day' : `${words[count - 1] ?? count} days`}
      </h2>
      <ol className="mt-3 grid gap-x-8 gap-y-2.5 sm:grid-flow-col sm:grid-cols-2" style={{ gridTemplateRows: `repeat(${Math.ceil(count / 2)}, auto)` }}>
        {track.itinerary.map((d) => (
          <li key={d.day} className="flex gap-3 text-sm">
            <span className="w-11 shrink-0 font-semibold text-laterite-600">Day {d.day}</span>
            <span className="text-stone-800">{d.summary}</span>
          </li>
        ))}
      </ol>
    </section>
  )
}

/** Avatar, name and "Sankri · led Kedarkantha 34 times". The name opens the guide's page. */
export function GuideLine({ guide, trekName, size = 'sm' }: { guide: GuideCard; trekName: string; size?: 'sm' | 'md' }) {
  const name = guide.full_name ?? 'Local guide'
  const record =
    guide.led_this_trek > 0
      ? `led ${trekName} ${guide.led_this_trek} ${guide.led_this_trek === 1 ? 'time' : 'times'}`
      : `new to ${trekName}`
  return (
    <Link to={`/guides/${guide.id}`} viewTransition className="group flex min-w-0 items-center gap-3">
      <Avatar url={guide.avatar_url} name={name} size={size} />
      <span className="min-w-0">
        <span className="block truncate font-semibold text-stone-900 group-hover:text-brand-800 group-hover:underline">
          {name}
        </span>
        <span className="block truncate text-xs text-stone-500">
          {[guide.home_city, record].filter(Boolean).join(' · ')}
        </span>
      </span>
    </Link>
  )
}

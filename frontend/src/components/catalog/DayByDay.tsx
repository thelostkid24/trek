import { useRef, useState } from 'react'
import type { ItineraryDay } from '../../api/catalog.ts'
import { addDays, dayLabel, toFeet } from '../../lib/format.ts'
import { TrekSection } from './TrekSections.tsx'

/** The day's bar: its high point or where you end, whichever is higher. The start is last night's camp. */
const chartAltitude = (d: ItineraryDay) =>
  Math.max(d.high_altitude_m ?? 0, d.end_altitude_m ?? 0) || d.start_altitude_m || null

const ft = (m: number) => toFeet(m).toLocaleString('en-IN')

/** "4 km · 6,455 → 8,860 ft · 4–5 hours". */
function dayMeta(d: ItineraryDay): string {
  const path = [d.start_altitude_m, d.high_altitude_m, d.end_altitude_m].filter((m): m is number => m !== null)
  const hours =
    d.hours_min !== null
      ? d.hours_max !== null && d.hours_max !== d.hours_min
        ? `${d.hours_min}–${d.hours_max} hours`
        : `${d.hours_min} ${d.hours_min === 1 ? 'hour' : 'hours'}`
      : null
  return [
    d.distance_km !== null && `${d.distance_km} km`,
    path.length > 0 && `${path.map(ft).join(' → ')} ft`,
    d.route_note,
    hours,
  ]
    .filter(Boolean)
    .join(' · ')
}

/**
 * "Day by day": an altitude bar per day (the summit day dark, the chosen day in laterite) over the day cards.
 * Tapping a bar or a card picks that day.
 */
export function DayByDay({ id, days, startDate }: { id: string; days: ItineraryDay[]; startDate?: string }) {
  const [selected, setSelected] = useState(1)
  const cards = useRef<HTMLOListElement>(null)
  if (days.length === 0) return null

  const heights = days.map(chartAltitude)
  const top = Math.max(...heights.map((h) => h ?? 0))
  const pick = (day: number, scroll: boolean) => {
    setSelected(day)
    if (scroll) cards.current?.children[day - 1]?.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
  }

  return (
    <TrekSection id={id} label="Day by day" aside={top > 0 ? 'tap a bar to highlight the day' : undefined}>
      {top > 0 && (
        <div className="rounded-2xl bg-white/80 p-4 ring-1 ring-paper-300 sm:p-5">
          <div className="flex h-44 items-end gap-2 border-b border-paper-300 sm:gap-3">
            {days.map((d, i) => {
              const h = heights[i]
              const chosen = d.day === selected
              const tone = chosen ? 'bg-laterite-600' : h === top ? 'bg-brand-900' : 'bg-sage-200'
              return (
                <button
                  key={d.day}
                  type="button"
                  onClick={() => pick(d.day, true)}
                  aria-pressed={chosen}
                  aria-label={`Day ${d.day}${h ? `, ${ft(h)} ft` : ''}`}
                  className="flex h-full min-w-0 flex-1 flex-col items-stretch justify-end gap-1"
                >
                  {h && (
                    <span className={`truncate text-center text-xs font-semibold ${chosen ? 'text-laterite-600' : 'text-stone-700'}`}>
                      {ft(h)} ft
                    </span>
                  )}
                  <span
                    className={`rounded-t-lg transition-colors ${tone}`}
                    style={{ height: h ? `${Math.max(8, (h / top) * 78)}%` : '0' }}
                  />
                </button>
              )
            })}
          </div>
          <div className="mt-2 flex gap-2 sm:gap-3">
            {days.map((d) => (
              <span
                key={d.day}
                className={`flex-1 text-center text-sm font-semibold ${d.day === selected ? 'text-laterite-600' : 'text-stone-800'}`}
              >
                Day {d.day}
              </span>
            ))}
          </div>
        </div>
      )}

      <ol ref={cards} className="mt-3 space-y-3">
        {days.map((d) => {
          const chosen = d.day === selected
          const meta = dayMeta(d)
          return (
            <li key={d.day}>
              <button
                type="button"
                onClick={() => pick(d.day, false)}
                aria-pressed={chosen}
                className={`grid w-full grid-cols-[4rem_1fr] gap-3 rounded-xl p-4 text-left ring-1 transition sm:grid-cols-[5rem_1fr] ${
                  chosen ? 'bg-white ring-laterite-600' : 'bg-white/60 ring-paper-300 hover:ring-stone-400'
                }`}
              >
                <span className={`text-sm font-semibold ${chosen ? 'text-laterite-600' : 'text-stone-800'}`}>
                  Day {d.day}
                  {startDate && (
                    <span className="mt-0.5 block text-xs font-normal text-stone-500">{dayLabel(addDays(startDate, d.day - 1))}</span>
                  )}
                </span>
                <span className="min-w-0">
                  <span className="block font-semibold text-stone-900">{d.summary}</span>
                  {meta && <span className="mt-0.5 block text-sm text-stone-500">{meta}</span>}
                  {d.description && <span className="mt-1.5 block text-stone-700">{d.description}</span>}
                </span>
              </button>
            </li>
          )
        })}
      </ol>
    </TrekSection>
  )
}

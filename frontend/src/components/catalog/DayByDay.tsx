import { useState } from 'react'
import type { ItineraryDay } from '../../api/catalog.ts'
import { addDays, dayLabel, toFeet } from '../../lib/format.ts'
import { TrekSection } from './TrekSections.tsx'

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
 * "Day by day": pick a day, read it. On wide screens the days are a list on the left with an oval highlight that
 * slides to the chosen day; on phones they're a row of chips that scrolls sideways. One compact panel shows the
 * chosen day.
 */
export function DayByDay({ id, days, startDate }: { id: string; days: ItineraryDay[]; startDate?: string }) {
  const [selected, setSelected] = useState(0)
  if (days.length === 0) return null
  const d = days[Math.min(selected, days.length - 1)]
  const meta = dayMeta(d)

  return (
    <TrekSection id={id} label="Day by day">
      <div className="grid gap-4 md:grid-cols-[9rem_1fr] md:gap-6">
        {/* Phones: a sideways-scrolling row of days. */}
        <div role="tablist" aria-label="Days" className="-mx-4 flex gap-2 overflow-x-auto px-4 pb-1 [scrollbar-width:none] md:hidden [&::-webkit-scrollbar]:hidden">
          {days.map((x, i) => (
            <button
              key={x.day}
              type="button"
              role="tab"
              aria-selected={i === selected}
              onClick={() => setSelected(i)}
              className={`shrink-0 rounded-full px-4 py-1.5 text-sm font-medium transition ${
                i === selected ? 'bg-brand-900 text-white' : 'bg-white/80 text-stone-700 ring-1 ring-paper-300'
              }`}
            >
              Day {x.day}
            </button>
          ))}
        </div>

        {/* Wide screens: an oval highlight slides behind the chosen day. */}
        <nav aria-label="Days" className="hidden md:block">
          <div className="relative">
            <span
              aria-hidden="true"
              className="absolute inset-x-0 h-9 rounded-full bg-brand-900 shadow-sm transition-transform duration-300 ease-[cubic-bezier(0.2,0.8,0.2,1)]"
              style={{ transform: `translateY(${selected * 2.25}rem)` }}
            />
            <ul className="relative">
              {days.map((x, i) => (
                <li key={x.day}>
                  <button
                    type="button"
                    aria-current={i === selected ? 'step' : undefined}
                    onClick={() => setSelected(i)}
                    className={`flex h-9 w-full items-center rounded-full px-4 text-left text-sm transition-colors ${
                      i === selected ? 'font-medium text-white' : 'text-stone-500 hover:text-stone-800'
                    }`}
                  >
                    Day {x.day}
                  </button>
                </li>
              ))}
            </ul>
          </div>
        </nav>

        <article aria-live="polite" className="min-w-0 rounded-xl bg-white/80 p-4 ring-1 ring-paper-300 sm:p-5">
          <p className="text-xs font-semibold tracking-[0.14em] text-laterite-600 uppercase">
            Day {d.day}
            {startDate && <span className="font-normal tracking-normal text-stone-500 normal-case"> · {dayLabel(addDays(startDate, d.day - 1))}</span>}
          </p>
          <h3 className="mt-1 text-lg font-semibold text-stone-900">{d.summary}</h3>
          {meta && <p className="mt-0.5 text-sm text-stone-500">{meta}</p>}
          {d.description && <p className="mt-2 text-stone-700">{d.description}</p>}
          {days.length > 1 && (
            <div className="mt-4 flex justify-between gap-3 border-t border-paper-200 pt-3 text-sm">
              <button
                type="button"
                disabled={selected === 0}
                onClick={() => setSelected(selected - 1)}
                className="font-medium text-brand-800 hover:text-brand-900 disabled:invisible"
              >
                ← Day {d.day - 1}
              </button>
              <button
                type="button"
                disabled={selected === days.length - 1}
                onClick={() => setSelected(selected + 1)}
                className="font-medium text-brand-800 hover:text-brand-900 disabled:invisible"
              >
                Day {d.day + 1} →
              </button>
            </div>
          )}
        </article>
      </div>
    </TrekSection>
  )
}

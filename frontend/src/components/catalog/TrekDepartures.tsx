import { useState } from 'react'
import { Link } from 'react-router-dom'
import type { GuideCard, TrekDeparture, TrekPage } from '../../api/catalog.ts'
import { monthKey, parseDate, rupees, shortRange } from '../../lib/format.ts'
import { isCertified } from '../../lib/trek.ts'
import { Avatar } from '../Avatar.tsx'
import { SectionLabel } from './TrekSections.tsx'

const COUNT_WORDS = ['No', 'One', 'Two', 'Three', 'Four', 'Five', 'Six', 'Seven', 'Eight', 'Nine', 'Ten']

const firstName = (g: GuideCard) => g.full_name?.split(' ')[0] ?? 'your guide'

/**
 * The departures column: the trek's price once (it doesn't depend on the guide), then one row per month that opens to
 * its dates, one card per date introducing its guide and leading on to the departure page, where they book.
 */
export function TrekDepartures({ trek }: { trek: TrekPage }) {
  const { track, departures } = trek
  const [month, setMonth] = useState<string | null>(null)

  const guides = [...new Map(departures.map((d) => [d.guide.id, d.guide])).values()]
  const months = [...new Set(departures.map((d) => monthKey(d.start_date)))]
  // The first month starts open; '' means the trekker closed them all.
  const activeMonth = month !== null && (month === '' || months.includes(month)) ? month : (months[0] ?? null)
  const shown = departures.filter((d) => monthKey(d.start_date) === activeMonth)
  const prices = departures.map((d) => d.price_paise)
  const lowest = Math.min(...prices)
  const highest = Math.max(...prices)

  const count = COUNT_WORDS[guides.length] ?? String(guides.length)
  const intro =
    guides.length === 1
      ? `One dai (mountain guide) leads ${track.name} this season.`
      : `${count} dai (mountain guides) lead ${track.name} this season, each with their own departures.`

  return (
    <section aria-labelledby="departures-heading">
      <SectionLabel id="departures-heading">Departures</SectionLabel>
      {departures.length === 0 ? (
        <p className="mt-3 rounded-xl bg-white/80 p-4 text-sm text-stone-600 ring-1 ring-paper-300">
          Upcoming: dates for this trek open soon.
        </p>
      ) : (
        <>
          <p className="mt-2 text-stone-700">
            {intro} Know your guide's eligibility and experience before the departure — you should know who you're
            going with.
          </p>
          <p className="mt-3 rounded-xl bg-white/80 px-4 py-3 text-sm text-stone-700 ring-1 ring-paper-300">
            <span className="font-semibold text-stone-900">
              {lowest === highest ? rupees(lowest) : `From ${rupees(lowest)}`}
            </span>{' '}
            per person {lowest === highest ? 'is what this trek costs' : 'for this trek'}, whichever guide you choose. The
            guide is your choice.
          </p>

          <div className="mt-4 space-y-2">
            {months.map((m) => {
              const on = m === activeMonth
              const inMonth = departures.filter((d) => monthKey(d.start_date) === m)
              return (
                <div key={m} className={`rounded-xl bg-white/80 ring-1 transition ${on ? 'ring-brand-900' : 'ring-paper-300'}`}>
                  <button
                    type="button"
                    aria-expanded={on}
                    onClick={() => setMonth(on ? '' : m)}
                    className="flex w-full items-center justify-between gap-3 px-4 py-3.5 text-left"
                  >
                    <span className="font-semibold text-stone-900">
                      {parseDate(`${m}-01`).toLocaleDateString('en-IN', { month: 'long', year: 'numeric' })}
                    </span>
                    <span className="flex items-center gap-3 text-sm text-stone-500">
                      {inMonth.length} {inMonth.length === 1 ? 'date' : 'dates'}
                      <svg viewBox="0 0 20 20" className={`size-4 text-stone-700 transition-transform ${on ? 'rotate-180' : ''}`} fill="currentColor" aria-hidden="true">
                        <path d="M5.5 7.5 10 12l4.5-4.5z" />
                      </svg>
                    </span>
                  </button>
                  {on && (
                    <ul className="space-y-3 px-3 pb-3">
                      {shown.map((d) => (
                        <DepartureCard key={d.id} departure={d} />
                      ))}
                    </ul>
                  )}
                </div>
              )
            })}
          </div>
        </>
      )}
    </section>
  )
}

/** One date: its guide at a glance (photo, name, languages, experience, certified, rating) and the way to book them. */
function DepartureCard({ departure: d }: { departure: TrekDeparture }) {
  const g = d.guide
  const status = d.seats_left === 0 ? 'Batch full' : !d.bookable ? 'Bookings closed' : null
  return (
    <li className="rounded-xl bg-white p-4 ring-1 ring-paper-300">
      <div className="flex items-center gap-4">
        <GuidePhoto guide={g} />
        <div className="min-w-0">
          <p className="text-lg font-semibold text-stone-900">{shortRange(d.start_date, d.end_date)}</p>
          <p className="text-sm font-medium text-stone-800">{g.full_name ?? 'Local guide'}</p>
          {g.languages && <p className="text-sm text-stone-600">Speaks {g.languages}</p>}
        </div>
      </div>
      <GuideHighlights guide={g} />
      {status ? (
        <p className="mt-4 text-sm font-medium text-stone-500">{status}</p>
      ) : (
        <Link
          to={`/departures/${d.id}`}
          viewTransition
          className="mt-4 block rounded-full bg-brand-900 px-4 py-3 text-center font-semibold text-white hover:bg-brand-800"
        >
          View {firstName(g)} and book →
        </Link>
      )}
    </li>
  )
}

/** A close, round crop of the guide's face. Profile photos are usually head and shoulders, so it zooms toward the top. */
function GuidePhoto({ guide: g }: { guide: GuideCard }) {
  if (!g.avatar_url) return <Avatar url={null} name={g.full_name} size="md" className="size-16! shrink-0" />
  return (
    <span className="block size-16 shrink-0 overflow-hidden rounded-full ring-2 ring-paper-200">
      <img src={g.avatar_url} alt={g.full_name ?? 'Guide'} className="size-full origin-[50%_20%] scale-125 object-cover object-[50%_20%]" />
    </span>
  )
}

/** Years guiding, certified, rating. No badge for guides without reviews yet. */
function GuideHighlights({ guide: g }: { guide: GuideCard }) {
  const chip = 'rounded-full px-2.5 py-1 text-xs font-medium'
  return (
    <p className="mt-3 flex flex-wrap gap-1.5">
      {g.years_leading !== null && g.years_leading > 0 && (
        <span className={`${chip} bg-brand-50 text-brand-900`}>
          {g.years_leading} {g.years_leading === 1 ? 'yr' : 'yrs'} guiding
        </span>
      )}
      {isCertified(g) && <span className={`${chip} bg-brand-50 text-brand-900`}>Certified</span>}
      {g.rating !== null && (
        <span className={`${chip} bg-laterite-100 text-laterite-600`}>
          {g.rating.toFixed(1)} ★
        </span>
      )}
    </p>
  )
}

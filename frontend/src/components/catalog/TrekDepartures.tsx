import { useState } from 'react'
import { Link } from 'react-router-dom'
import type { GuideCard, TrekDeparture, TrekPage } from '../../api/catalog.ts'
import { monthKey, parseDate, rupees, shortRange } from '../../lib/format.ts'
import { Avatar } from '../Avatar.tsx'
import { SectionLabel } from './TrekSections.tsx'

const COUNT_WORDS = ['No', 'One', 'Two', 'Three', 'Four', 'Five', 'Six', 'Seven', 'Eight', 'Nine', 'Ten']

const firstName = (g: GuideCard) => g.full_name?.split(' ')[0] ?? 'your guide'

/**
 * The departures column: one row per month that opens to its dates, one card per date. Each card expands, independently
 * of the others, to introduce its guide (credentials, rating) and lead on to the departure page, where they book.
 */
export function TrekDepartures({ trek }: { trek: TrekPage }) {
  const { track, departures } = trek
  const [month, setMonth] = useState<string | null>(null)
  const [openIds, setOpenIds] = useState<ReadonlySet<string>>(new Set())

  const guides = [...new Map(departures.map((d) => [d.guide.id, d.guide])).values()]
  const months = [...new Set(departures.map((d) => monthKey(d.start_date)))]
  // The first month starts open; '' means the trekker closed them all.
  const activeMonth = month !== null && (month === '' || months.includes(month)) ? month : (months[0] ?? null)
  const shown = departures.filter((d) => monthKey(d.start_date) === activeMonth)
  const toggle = (id: string) =>
    setOpenIds((prev) => {
      const next = new Set(prev)
      if (!next.delete(id)) next.add(id)
      return next
    })

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
          No dates are open right now. Check back soon.
        </p>
      ) : (
        <>
          <p className="mt-2 text-stone-700">
            {intro} Know your guide's eligibility and experience before the departure — you should know who you're
            going with.
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
                        <DepartureCard
                          key={d.id}
                          departure={d}
                          trekName={track.name}
                          open={openIds.has(d.id)}
                          onToggle={() => toggle(d.id)}
                        />
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

function DepartureCard({
  departure: d,
  trekName,
  open,
  onToggle,
}: {
  departure: TrekDeparture
  trekName: string
  open: boolean
  onToggle: () => void
}) {
  const status = d.seats_left === 0 ? 'Batch full' : !d.bookable ? 'Bookings closed' : null
  return (
    <li className={`rounded-xl bg-white ring-1 transition ${open ? 'ring-stone-900' : 'ring-paper-300 hover:ring-stone-400'}`}>
      <button type="button" onClick={onToggle} aria-expanded={open} className="block w-full p-4 text-left">
        <span className="flex items-baseline justify-between gap-3">
          <span className="text-lg font-semibold text-stone-900">{shortRange(d.start_date, d.end_date)}</span>
          <span className="font-semibold text-stone-900">{rupees(d.price_paise)}</span>
        </span>
        <span className="mt-1.5 block text-sm text-stone-600">Led by {d.guide.full_name ?? 'a local guide'}</span>
        <GuideHighlights guide={d.guide} />
        {status && <span className="mt-2 block text-sm font-medium text-stone-500">{status}</span>}
        <span className="mt-3 flex items-center gap-1 text-sm font-medium text-brand-800">
          {open ? 'Show less' : `More about ${firstName(d.guide)} & view departure`}
          <svg viewBox="0 0 20 20" className={`size-4 transition-transform ${open ? 'rotate-180' : ''}`} fill="currentColor" aria-hidden="true">
            <path d="M5.5 7.5 10 12l4.5-4.5z" />
          </svg>
        </span>
      </button>
      {open && <GuideIntro departure={d} trekName={trekName} />}
    </li>
  )
}

/** What sets guides apart at a glance: rating and reviews, times they've led this trek, years leading. */
function GuideHighlights({ guide: g }: { guide: GuideCard }) {
  const chip = 'rounded-full px-2.5 py-1 text-xs font-medium'
  return (
    <span className="mt-2.5 flex flex-wrap gap-1.5">
      {g.rating !== null ? (
        <span className={`${chip} bg-laterite-100 text-laterite-600`}>
          ★ {g.rating.toFixed(1)} ({g.review_count})
        </span>
      ) : (
        <span className={`${chip} bg-paper-200 text-stone-600`}>New guide</span>
      )}
      {g.led_this_trek > 0 && <span className={`${chip} bg-brand-50 text-brand-900`}>Led this trek {g.led_this_trek}×</span>}
      {g.years_leading !== null && g.years_leading > 0 && (
        <span className={`${chip} bg-brand-50 text-brand-900`}>
          {g.years_leading} {g.years_leading === 1 ? 'yr' : 'yrs'} guiding
        </span>
      )}
    </span>
  )
}

/** Who leads this date: credentials, rating and their own words, then their page. */
function GuideIntro({ departure: d, trekName }: { departure: TrekDeparture; trekName: string }) {
  const g = d.guide
  const name = firstName(g)
  const record = [
    g.years_leading !== null && `${g.years_leading} ${g.years_leading === 1 ? 'year' : 'years'} leading`,
    g.led_this_trek > 0 && `${g.led_this_trek} ${trekName} ${g.led_this_trek === 1 ? 'summit' : 'summits'}`,
    g.languages,
  ]
    .filter(Boolean)
    .join(' · ')

  return (
    <div className="border-t border-paper-200 px-4 pt-4 pb-4">
      <div className="flex gap-3">
        {g.avatar_url ? (
          <img src={g.avatar_url} alt={`${g.full_name ?? 'Guide'}'s photo`} className="h-20 w-16 shrink-0 rounded-lg object-cover" />
        ) : (
          <Avatar url={null} name={g.full_name} size="md" className="rounded-lg!" />
        )}
        <div className="min-w-0">
          <p className="font-semibold text-stone-900">
            {g.full_name ?? 'Local guide'}
            {g.home_city && <> — {g.home_city}</>}
          </p>
          {record && <p className="mt-0.5 text-sm text-stone-600">{record}</p>}
        </div>
      </div>
      {(g.bmc_institute || g.amc_institute) && (
        <p className="mt-3 text-sm text-stone-700">
          {[g.bmc_institute && `BMC, ${g.bmc_institute}`, g.amc_institute && `AMC, ${g.amc_institute}`].filter(Boolean).join(' · ')}
        </p>
      )}
      <p className="mt-2 text-sm text-stone-600">
        {g.rating !== null ? (
          <>
            <span className="font-semibold text-stone-900">{g.rating.toFixed(1)}</span> · {g.review_count}{' '}
            {g.review_count === 1 ? 'review' : 'reviews'}
          </>
        ) : (
          'No reviews yet'
        )}
      </p>

      <Link
        to={`/departures/${d.id}`}
        viewTransition
        className="mt-4 block rounded-full bg-brand-900 px-4 py-3 text-center font-semibold text-white hover:bg-brand-800"
      >
        View the departure →
      </Link>
      <p className="mt-3 text-center text-sm text-stone-600">
        The full price and {name}'s profile are on the next page. Book from there.
      </p>
    </div>
  )
}

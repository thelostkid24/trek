import { useState } from 'react'
import { Link } from 'react-router-dom'
import type { GuideCard, TrekDeparture, TrekPage } from '../../api/catalog.ts'
import { monthKey, parseDate, rupees, shortRange } from '../../lib/format.ts'
import { Avatar } from '../Avatar.tsx'
import { SectionLabel } from './TrekSections.tsx'

const COUNT_WORDS = ['No', 'One', 'Two', 'Three', 'Four', 'Five', 'Six', 'Seven', 'Eight', 'Nine', 'Ten']

const firstName = (g: GuideCard) => g.full_name?.split(' ')[0] ?? 'your guide'

/**
 * The departures column: filter by guide and month, then one card per date. The open card introduces its guide
 * (credentials, rating, quote) and leads on to the departure page, where they book, or the guide's page.
 */
export function TrekDepartures({ trek }: { trek: TrekPage }) {
  const { track, departures, charity } = trek
  const [guideId, setGuideId] = useState<string | null>(null)
  const [month, setMonth] = useState<string | null>(null)
  const [openId, setOpenId] = useState<string | null>(null)

  const guides = [...new Map(departures.map((d) => [d.guide.id, d.guide])).values()]
  const forGuide = departures.filter((d) => guideId === null || d.guide.id === guideId)
  const months = [...new Set(forGuide.map((d) => monthKey(d.start_date)))]
  const activeMonth = month !== null && months.includes(month) ? month : (months[0] ?? null)
  const shown = forGuide.filter((d) => monthKey(d.start_date) === activeMonth)
  // The first date starts open, until the trekker picks another (or closes it).
  const open = openId === null ? shown[0]?.id : openId

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

          {guides.length > 1 && (
            <div className="mt-4 flex flex-wrap gap-2" role="group" aria-label="Filter by guide">
              <Chip on={guideId === null} onClick={() => { setGuideId(null); setOpenId(null) }}>
                All guides
              </Chip>
              {guides.map((g) => (
                <Chip key={g.id} on={guideId === g.id} onClick={() => { setGuideId(g.id); setOpenId(null) }}>
                  {firstName(g)}
                </Chip>
              ))}
            </div>
          )}

          <div role="tablist" aria-label="Month" className="mt-3 flex overflow-x-auto rounded-xl bg-white/80 ring-1 ring-paper-300 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
            {months.map((m) => {
              const on = m === activeMonth
              const dates = forGuide.filter((d) => monthKey(d.start_date) === m).length
              return (
                <button
                  key={m}
                  type="button"
                  role="tab"
                  aria-selected={on}
                  onClick={() => { setMonth(m); setOpenId(null) }}
                  className={`min-w-[4.5rem] flex-1 rounded-xl px-2 py-2 text-center transition ${on ? 'bg-brand-900 text-white' : 'text-stone-800 hover:bg-paper-100'}`}
                >
                  <span className="block text-sm font-semibold">
                    {parseDate(`${m}-01`).toLocaleDateString('en-IN', { month: 'short' })}
                  </span>
                  <span className={`block text-[0.7rem] ${on ? 'text-brand-100' : 'text-stone-500'}`}>
                    {dates} {dates === 1 ? 'date' : 'dates'}
                  </span>
                </button>
              )
            })}
          </div>

          <ul className="mt-3 space-y-3">
            {shown.map((d) => (
              <DepartureCard
                key={d.id}
                departure={d}
                trekName={track.name}
                open={d.id === open}
                onToggle={() => setOpenId(d.id === open ? '' : d.id)}
                charity={charity}
              />
            ))}
          </ul>
        </>
      )}
    </section>
  )
}

function Chip({ on, onClick, children }: { on: boolean; onClick: () => void; children: string }) {
  return (
    <button
      type="button"
      aria-pressed={on}
      onClick={onClick}
      className={`rounded-full px-3.5 py-1.5 text-sm transition ${on ? 'bg-brand-900 text-white' : 'bg-white/80 text-stone-800 ring-1 ring-paper-300 hover:ring-stone-400'}`}
    >
      {children}
    </button>
  )
}

function DepartureCard({
  departure: d,
  trekName,
  open,
  onToggle,
  charity,
}: {
  departure: TrekDeparture
  trekName: string
  open: boolean
  onToggle: () => void
  charity: TrekPage['charity']
}) {
  const taken = d.max_group_size - d.seats_left
  const status = d.seats_left === 0 ? 'Batch full' : !d.bookable ? 'Bookings closed' : `${d.seats_left} of ${d.max_group_size} seats left`
  return (
    <li className={`rounded-xl bg-white ring-1 transition ${open ? 'ring-stone-900' : 'ring-paper-300 hover:ring-stone-400'}`}>
      <button type="button" onClick={onToggle} aria-expanded={open} className="block w-full p-4 text-left">
        <span className="flex items-baseline justify-between gap-3">
          <span className="text-lg font-semibold text-stone-900">{shortRange(d.start_date, d.end_date)}</span>
          <span className="font-semibold text-stone-900">{rupees(d.price_paise)}</span>
        </span>
        <span className="mt-1.5 flex items-center gap-2 text-sm text-stone-600">
          <Avatar url={d.guide.avatar_url} name={d.guide.full_name} size="xs" />
          with {d.guide.full_name ?? 'a local guide'}
        </span>
        <span className="mt-3 flex gap-1" aria-hidden="true">
          {Array.from({ length: d.max_group_size }, (_, i) => (
            <span key={i} className={`h-1.5 flex-1 rounded-full ${i < taken ? 'bg-brand-900' : 'bg-paper-200'}`} />
          ))}
        </span>
        <span className={`mt-2 block text-sm font-medium ${d.bookable && d.seats_left <= 2 ? 'text-laterite-600' : 'text-stone-800'}`}>
          {status}
        </span>
      </button>
      {open && <GuideIntro departure={d} trekName={trekName} charity={charity} />}
    </li>
  )
}

/** Who leads this date: credentials, rating and their own words, then their page. */
function GuideIntro({ departure: d, trekName, charity }: { departure: TrekDeparture; trekName: string; charity: TrekPage['charity'] }) {
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
      {g.certification && (
        <p className="mt-3 text-sm text-stone-700">
          Certification: {g.certification}
          {g.certification_number && <>, number {g.certification_number}</>}
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
      {g.quote && <blockquote className="mt-3 border-l-2 border-paper-300 pl-3 font-serif text-stone-700 italic">“{g.quote}”</blockquote>}

      <div className="mt-4 grid gap-2 sm:grid-cols-2 lg:grid-cols-1">
        <Link
          to={`/departures/${d.id}`}
          viewTransition
          className="block rounded-lg bg-brand-900 px-4 py-3 text-center font-semibold text-white hover:bg-brand-800"
        >
          View these dates →
        </Link>
        <Link
          to={`/guides/${g.id}`}
          viewTransition
          className="block rounded-lg bg-white px-4 py-3 text-center font-semibold text-stone-900 ring-1 ring-paper-300 hover:ring-stone-400"
        >
          Get to know {name}
        </Link>
      </div>
      <p className="mt-3 text-center text-sm text-stone-600">
        The full price, what's covered and {name}'s record are on the next page. Book from there.
      </p>
      {charity && (
        <p className="mt-2 text-center text-xs text-stone-500">
          {charity.bps / 100}% goes to {charity.name}, and the rest runs the company.
        </p>
      )}
    </div>
  )
}

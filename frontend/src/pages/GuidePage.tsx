import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { getGuide, type DepartureSummary, type GuideProfile } from '../api/catalog.ts'
import { ApiError } from '../api/client.ts'
import { messageFor } from '../auth/errorMessages.ts'
import { Avatar } from '../components/Avatar.tsx'
import { SeatMeter } from '../components/catalog/DeparturePieces.tsx'
import { rupees, shortRange, weekdaysAndYear } from '../lib/format.ts'

/** /guides/:id — public. Who you'd walk with: their home, record and next departures. */
export function GuidePage() {
  const { id = '' } = useParams()
  const guide = useQuery({
    queryKey: ['public-guide', id],
    queryFn: () => getGuide(id),
    retry: (count, error) => !(error instanceof ApiError && error.status === 404) && count < 2,
  })

  if (guide.isPending) {
    return (
      <div className="mx-auto max-w-4xl space-y-4 px-4 py-10" aria-busy="true" aria-label="Loading guide">
        <div className="h-40 animate-pulse rounded-2xl bg-stone-200" />
        <div className="h-64 animate-pulse rounded-2xl bg-stone-100" />
      </div>
    )
  }
  if (guide.isError) {
    const missing = guide.error instanceof ApiError && guide.error.status === 404
    return (
      <section className="mx-auto max-w-md px-4 py-16 text-center">
        <h1 className="font-display text-2xl font-semibold">{missing ? 'Guide not found' : 'Something went wrong'}</h1>
        <p className="mt-2 text-stone-600">{missing ? 'This guide may no longer be leading treks.' : messageFor(guide.error)}</p>
        <Link to="/#treks" className="mt-4 inline-block text-brand-700 underline">
          See all treks
        </Link>
      </section>
    )
  }
  return <Profile guide={guide.data} />
}

function Profile({ guide: g }: { guide: GuideProfile }) {
  const name = g.full_name ?? 'Local guide'
  const firstName = name.split(' ')[0]
  const facts = [
    g.home_city,
    g.treks_led > 0 ? `led ${g.treks_led} ${g.treks_led === 1 ? 'trek' : 'treks'} with us` : 'new with us',
  ].filter(Boolean)

  return (
    <div className="mx-auto max-w-4xl space-y-8 px-4 py-8 sm:py-12">
      <button type="button" onClick={() => window.history.back()} className="text-sm text-brand-700 hover:text-brand-900">
        ← Back
      </button>

      <header className="flex flex-col items-start gap-5 sm:flex-row sm:items-center">
        <Avatar url={g.avatar_url} name={name} size="lg" />
        <div>
          <p className="text-xs font-semibold tracking-[0.18em] text-laterite-600 uppercase">Your guide</p>
          <h1 className="mt-1 font-display text-3xl font-semibold tracking-tight sm:text-4xl">{name}</h1>
          <p className="mt-1 text-stone-600">{facts.join(' · ')}</p>
        </div>
      </header>

      {g.bio && <p className="max-w-2xl whitespace-pre-line text-stone-700">{g.bio}</p>}

      <div className="grid gap-8 lg:grid-cols-[1fr_360px] lg:items-start">
        <section>
          <h2 className="text-xs font-semibold tracking-[0.14em] text-stone-500 uppercase">
            Walk with {firstName}
          </h2>
          {g.upcoming.length === 0 ? (
            <p className="mt-3 rounded-2xl bg-white p-5 text-sm text-stone-600 ring-1 ring-stone-200">
              No upcoming departures right now.
            </p>
          ) : (
            <ul className="mt-3 space-y-3">
              {g.upcoming.map((d) => (
                <UpcomingRow key={d.id} departure={d} />
              ))}
            </ul>
          )}
        </section>

        <section>
          <h2 className="text-xs font-semibold tracking-[0.14em] text-stone-500 uppercase">Treks led</h2>
          {g.treks.length === 0 ? (
            <p className="mt-3 text-sm text-stone-600">{firstName} hasn't completed a trek with Sahyātri yet.</p>
          ) : (
            <ul className="mt-3 divide-y divide-stone-100 rounded-2xl bg-white ring-1 ring-stone-200">
              {g.treks.map((t) => (
                <li key={t.track.slug} className="flex items-center justify-between gap-3 px-4 py-3 text-sm">
                  <Link to={`/treks/${t.track.slug}`} className="font-medium text-stone-900 hover:text-brand-800 hover:underline">
                    {t.track.name}
                  </Link>
                  <span className="text-stone-500">
                    {t.times} {t.times === 1 ? 'time' : 'times'}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </div>
  )
}

function UpcomingRow({ departure: d }: { departure: DepartureSummary }) {
  return (
    <li className="rounded-2xl bg-white p-4 ring-1 ring-stone-200">
      <div className="flex items-baseline justify-between gap-3">
        <Link to={`/treks/${d.track.slug}`} className="font-display text-lg font-semibold hover:text-brand-800">
          {d.track.name}
        </Link>
        <span className="font-semibold">{rupees(d.price_paise)}</span>
      </div>
      <p className="text-sm text-stone-600">
        {shortRange(d.start_date, d.end_date)} · {weekdaysAndYear(d.start_date, d.end_date)}
      </p>
      <div className="mt-3">
        <SeatMeter size={d.max_group_size} left={d.seats_left} />
      </div>
      <div className="mt-3 flex gap-2">
        <Link
          to={`/departures/${d.id}`}
          className="rounded-full bg-white px-4 py-2 text-sm font-medium text-stone-700 ring-1 ring-stone-300 hover:ring-stone-400"
        >
          Details
        </Link>
        {d.bookable && (
          <Link to={`/book/${d.id}`} className="rounded-full bg-brand-900 px-4 py-2 text-sm font-medium text-white hover:bg-brand-800">
            Book
          </Link>
        )}
      </div>
    </li>
  )
}

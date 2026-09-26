import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { getGuide, type DepartureSummary, type GuideProfile } from '../api/catalog.ts'
import { ApiError } from '../api/client.ts'
import { messageFor } from '../auth/errorMessages.ts'
import { Avatar } from '../components/Avatar.tsx'
import { FillBar } from '../components/catalog/DeparturePieces.tsx'
import { RatingLine } from '../components/catalog/GuideProfileCard.tsx'
import { SectionLabel } from '../components/catalog/TrekSections.tsx'
import { parseDate, rupees, shortRange, weekdaysAndYear } from '../lib/format.ts'

/**
 * /guides/:id — public. "Know your guide": who they are, their credentials and record, what trekkers say, and the
 * dates they lead, each bookable from here. Contract: docs/TRD.md §7.7.
 */
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
  const first = name.split(' ')[0]
  const stats: [string, string][] = [
    ...(g.years_leading !== null ? [['Leading treks', `${g.years_leading} ${g.years_leading === 1 ? 'year' : 'years'}`] as [string, string]] : []),
    ['Treks led with us', String(g.treks_led)],
    ['Rating', g.rating !== null ? `${g.rating.toFixed(1)} of 5` : 'No reviews yet'],
    ...(g.languages ? [['Speaks', g.languages] as [string, string]] : []),
  ]

  return (
    <div className="bg-paper-50">
      <div className="mx-auto max-w-5xl space-y-10 px-4 py-8 sm:py-12">
        <button type="button" onClick={() => window.history.back()} className="text-sm text-brand-700 hover:text-brand-900">
          ← Back
        </button>

        <header className="grid gap-6 sm:grid-cols-[auto_1fr] sm:items-center">
          {g.avatar_url ? (
            <img src={g.avatar_url} alt={`${name}'s photo`} className="h-44 w-36 rounded-2xl object-cover" />
          ) : (
            <Avatar url={null} name={name} size="lg" className="size-36! rounded-2xl! text-5xl!" />
          )}
          <div>
            <p className="text-xs font-semibold tracking-[0.16em] text-laterite-600 uppercase">Know your guide</p>
            <h1 className="mt-1 font-serif text-5xl font-light leading-none tracking-tight text-stone-900">{name}</h1>
            {g.home_city && <p className="mt-2 text-lg text-stone-600">from {g.home_city}</p>}
            <p className="mt-2">
              <RatingLine rating={g.rating} count={g.review_count} />
            </p>
          </div>
        </header>

        <dl className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          {stats.map(([label, value]) => (
            <div key={label} className="rounded-xl bg-white px-4 py-3 ring-1 ring-paper-300">
              <dt className="text-[0.65rem] font-medium tracking-[0.12em] text-stone-500 uppercase">{label}</dt>
              <dd className="mt-1 font-semibold text-stone-900">{value}</dd>
            </div>
          ))}
        </dl>

        <div className="grid gap-8 lg:grid-cols-[1fr_22rem] lg:items-start">
          <div className="space-y-8">
            {(g.quote || g.bio) && (
              <section className="space-y-4">
                {g.quote && (
                  <blockquote className="border-l-2 border-laterite-400 pl-4 font-serif text-2xl leading-snug text-stone-800 italic">
                    “{g.quote}”
                  </blockquote>
                )}
                {g.bio && <p className="max-w-2xl whitespace-pre-line text-stone-700">{g.bio}</p>}
              </section>
            )}

            <section>
              <SectionLabel>Credentials</SectionLabel>
              <dl className="mt-3 divide-y divide-paper-200 rounded-xl bg-white px-4 ring-1 ring-paper-300">
                <Credential label="Certification" value={g.certification} />
                <Credential label="Certificate number" value={g.certification_number} />
                <Credential label="Leading treks since" value={g.years_leading !== null ? `${new Date().getFullYear() - g.years_leading}` : null} />
                <Credential label="Languages" value={g.languages} />
              </dl>
            </section>

            <section>
              <SectionLabel aside={g.review_count > 0 ? `${g.review_count} ${g.review_count === 1 ? 'review' : 'reviews'}` : undefined}>
                What trekkers say
              </SectionLabel>
              {g.reviews.length === 0 ? (
                <p className="mt-3 text-sm text-stone-600">Reviews appear here after {first}'s treks are completed.</p>
              ) : (
                <ul className="mt-3 grid gap-3 sm:grid-cols-2">
                  {g.reviews.map((r, i) => (
                    <li key={i} className="rounded-xl bg-white p-4 ring-1 ring-paper-300">
                      <p className="tracking-wider text-laterite-600" aria-label={`${r.rating} out of 5`}>
                        {'★'.repeat(r.rating)}
                        <span className="text-paper-300">{'★'.repeat(5 - r.rating)}</span>
                      </p>
                      {r.body && <p className="mt-2 text-stone-700">{r.body}</p>}
                      <p className="mt-3 text-xs text-stone-500">
                        {r.author_name} · {r.trek_name},{' '}
                        {parseDate(r.trek_start_date).toLocaleDateString('en-IN', { month: 'short', year: 'numeric' })}
                      </p>
                    </li>
                  ))}
                </ul>
              )}
            </section>

            {g.treks.length > 0 && (
              <section>
                <SectionLabel>Treks led</SectionLabel>
                <ul className="mt-3 divide-y divide-paper-200 rounded-xl bg-white ring-1 ring-paper-300">
                  {g.treks.map((t) => (
                    <li key={t.track.slug} className="flex items-center justify-between gap-3 px-4 py-3 text-sm">
                      <Link to={`/treks/${t.track.slug}`} viewTransition className="font-medium text-stone-900 hover:text-brand-800 hover:underline">
                        {t.track.name}
                      </Link>
                      <span className="text-stone-500">
                        {t.times} {t.times === 1 ? 'time' : 'times'}
                      </span>
                    </li>
                  ))}
                </ul>
              </section>
            )}
          </div>

          <section className="lg:sticky lg:top-20">
            <SectionLabel>Walk with {first}</SectionLabel>
            {g.upcoming.length === 0 ? (
              <p className="mt-3 rounded-xl bg-white p-4 text-sm text-stone-600 ring-1 ring-paper-300">No upcoming departures right now.</p>
            ) : (
              <ul className="mt-3 space-y-3">
                {g.upcoming.map((d) => (
                  <UpcomingRow key={d.id} departure={d} />
                ))}
              </ul>
            )}
          </section>
        </div>
      </div>
    </div>
  )
}

function Credential({ label, value }: { label: string; value: string | null }) {
  return (
    <div className="flex justify-between gap-4 py-3 text-sm">
      <dt className="text-stone-500">{label}</dt>
      <dd className={`text-right ${value ? 'font-medium text-stone-900' : 'text-stone-400'}`}>{value ?? 'Not added yet'}</dd>
    </div>
  )
}

function UpcomingRow({ departure: d }: { departure: DepartureSummary }) {
  return (
    <li className="rounded-xl bg-white p-4 ring-1 ring-paper-300">
      <div className="flex items-baseline justify-between gap-3">
        <Link to={`/departures/${d.id}`} viewTransition className="font-semibold text-stone-900 hover:text-brand-800">
          {d.track.name}
        </Link>
        <span className="font-semibold">{rupees(d.price_paise)}</span>
      </div>
      <p className="text-sm text-stone-600">
        {shortRange(d.start_date, d.end_date)} · {weekdaysAndYear(d.start_date, d.end_date)}
      </p>
      <div className="mt-3">
        <FillBar size={d.max_group_size} left={d.seats_left} bookable={d.bookable} />
      </div>
      <div className="mt-3 flex gap-2">
        <Link
          to={`/departures/${d.id}`}
          className="rounded-full bg-white px-4 py-2 text-sm font-medium text-stone-700 ring-1 ring-paper-300 hover:ring-stone-400"
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

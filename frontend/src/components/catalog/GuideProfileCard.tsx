import { Link } from 'react-router-dom'
import type { GuideCard } from '../../api/catalog.ts'
import { Avatar } from '../Avatar.tsx'

/** "★ 4.7 · 3 reviews", or "No reviews yet". */
export function RatingLine({ rating, count, className = '' }: { rating: number | null; count: number; className?: string }) {
  if (rating === null) return <span className={`text-stone-500 ${className}`}>No reviews yet</span>
  return (
    <span className={className}>
      <span className="text-laterite-600" aria-hidden="true">
        ★
      </span>{' '}
      <span className="font-semibold text-stone-900">{rating.toFixed(1)}</span>
      <span className="text-stone-500">
        {' '}
        · {count} {count === 1 ? 'review' : 'reviews'}
      </span>
    </span>
  )
}

/**
 * The departure's guide, shown above the Book button so nobody commits before knowing who they walk with:
 * photo, home, record on this trek, languages, certification, rating and their own words, then their page.
 */
export function GuideProfileCard({ guide: g, trekName }: { guide: GuideCard; trekName: string }) {
  const name = g.full_name ?? 'Your guide'
  const first = name.split(' ')[0]
  const verified = g.certification !== null && g.certification_number !== null
  const facts: [string, string][] = [
    ...(g.years_leading !== null ? [['Leading treks', `${g.years_leading} ${g.years_leading === 1 ? 'year' : 'years'}`] as [string, string]] : []),
    [`${trekName} summits`, g.led_this_trek > 0 ? String(g.led_this_trek) : 'First time leading it'],
    ...(g.languages ? [['Speaks', g.languages] as [string, string]] : []),
  ]

  return (
    <section aria-labelledby="guide-heading" className="rounded-2xl bg-white p-5 ring-1 ring-paper-300 sm:p-6">
      <p className="text-xs font-semibold tracking-[0.16em] text-laterite-600 uppercase">Your guide</p>
      <div className="mt-3 flex gap-4">
        {g.avatar_url ? (
          <img src={g.avatar_url} alt={`${name}'s photo`} className="h-24 w-20 shrink-0 rounded-xl object-cover" />
        ) : (
          <Avatar url={null} name={name} size="lg" className="rounded-xl!" />
        )}
        <div className="min-w-0">
          <h2 id="guide-heading" className="font-serif text-2xl leading-tight text-stone-900">
            {name}
          </h2>
          {g.home_city && <p className="text-stone-600">from {g.home_city}</p>}
          <p className="mt-1.5 text-sm">
            <RatingLine rating={g.rating} count={g.review_count} />
          </p>
          {verified && (
            <p className="mt-2 inline-flex items-center gap-1.5 rounded-full bg-brand-50 px-2.5 py-0.5 text-xs font-medium text-brand-800">
              <svg viewBox="0 0 20 20" className="size-3.5" fill="currentColor" aria-hidden="true">
                <path d="M10 1.5 3.5 4v5c0 4 2.8 7.6 6.5 9 3.7-1.4 6.5-5 6.5-9V4L10 1.5Zm-1 12L5.5 10l1.4-1.4L9 10.7l4.1-4.1 1.4 1.4L9 13.5Z" />
              </svg>
              Certified
            </p>
          )}
        </div>
      </div>

      <dl className="mt-5 grid grid-cols-2 gap-3 sm:grid-cols-3">
        {facts.map(([label, value]) => (
          <div key={label} className="rounded-xl bg-paper-50 px-3.5 py-3 ring-1 ring-paper-200">
            <dt className="text-[0.65rem] font-medium tracking-[0.12em] text-stone-500 uppercase">{label}</dt>
            <dd className="mt-1 font-semibold text-stone-900">{value}</dd>
          </div>
        ))}
      </dl>
      {g.certification && (
        <p className="mt-4 text-sm text-stone-700">
          <span className="font-medium text-stone-900">Certification:</span> {g.certification}
          {g.certification_number && <>, number {g.certification_number}</>}
        </p>
      )}
      {g.quote && <blockquote className="mt-4 border-l-2 border-laterite-400 pl-4 font-serif text-lg text-stone-700 italic">“{g.quote}”</blockquote>}

      <Link
        to={`/guides/${g.id}`}
        viewTransition
        className="mt-5 inline-flex items-center gap-1 font-semibold text-brand-800 hover:text-brand-900"
      >
        Know your guide — {first}'s full profile and reviews →
      </Link>
    </section>
  )
}

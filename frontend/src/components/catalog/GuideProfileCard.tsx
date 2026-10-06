import { useState } from 'react'
import { Link } from 'react-router-dom'
import type { GuideCard, GuideCredentials, PublicReview } from '../../api/catalog.ts'
import { parseDate } from '../../lib/format.ts'
import { isCertified } from '../../lib/trek.ts'
import { IconFacts, type Fact } from './IconFacts.tsx'
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
 * photo, home, record on this trek, languages, rating, their bio and own words, then their page. `departureId`
 * rides along so the guide page can offer this departure to book.
 */
export function GuideProfileCard({ guide: g, trekName, departureId }: { guide: GuideCard; trekName: string; departureId: string }) {
  const name = g.full_name ?? 'Your guide'
  const first = name.split(' ')[0]
  const verified = isCertified(g)
  // Only BMC and AMC are the IMF-recognised courses; another certificate alone doesn't earn this line.
  const imfCourses = [
    g.bmc_institute !== null && g.bmc_certificate_number !== null && 'BMC',
    g.amc_institute !== null && g.amc_certificate_number !== null && 'AMC',
  ].filter(Boolean)
  const facts: Fact[] = [
    ...(g.years_leading !== null
      ? [{ label: 'Leading treks', value: `${g.years_leading} ${g.years_leading === 1 ? 'year' : 'years'}`, icon: 'calendar' as const }]
      : []),
    { label: `${trekName} summits`, value: g.led_this_trek > 0 ? String(g.led_this_trek) : 'First time leading it', icon: 'summit' },
    ...(g.languages ? [{ label: 'Speaks', value: g.languages, icon: 'speech' as const }] : []),
  ]

  return (
    <section aria-labelledby="guide-heading" className="rounded-2xl bg-white p-5 ring-1 ring-paper-300 sm:p-6">
      <p className="text-xs font-semibold tracking-[0.16em] text-laterite-600 uppercase">Your guide</p>
      <div className="mt-3 flex gap-4">
        {g.avatar_url ? (
          <img src={g.avatar_url} alt={name} className="h-24 w-20 shrink-0 rounded-xl object-cover" />
        ) : (
          <Avatar url={null} name={name} size="lg" className="rounded-xl!" />
        )}
        <div className="min-w-0">
          <h2 id="guide-heading" className="font-serif text-2xl leading-tight text-stone-900">
            {name}
          </h2>
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

      {imfCourses.length > 0 && (
        <div className="mt-5 flex items-start gap-3 rounded-xl bg-brand-50 p-4 ring-1 ring-brand-700/30">
          <svg viewBox="0 0 20 20" className="mt-0.5 size-5 shrink-0 text-brand-800" fill="currentColor" aria-hidden="true">
            <path d="M10 1.5 3.5 4v5c0 4 2.8 7.6 6.5 9 3.7-1.4 6.5-5 6.5-9V4L10 1.5Zm-1 12L5.5 10l1.4-1.4L9 10.7l4.1-4.1 1.4 1.4L9 13.5Z" />
          </svg>
          <p className="text-sm text-stone-700">
            <span className="block font-semibold text-brand-900">Certified to lead {trekName}</span>
            {first} holds the {imfCourses.join(' and ')} {imfCourses.length === 1 ? 'course' : 'courses'}, recognised by the Indian
            Mountaineering Foundation (IMF).
          </p>
        </div>
      )}

      <IconFacts facts={facts} className="mt-5 grid grid-cols-2 gap-x-6 gap-y-5 sm:grid-cols-3" />
      {g.bio && <Bio text={g.bio} />}
      {g.quote && <blockquote className="mt-4 border-l-2 border-laterite-400 pl-4 font-serif text-lg text-stone-700 italic">“{g.quote}”</blockquote>}

      <Link
        to={`/guides/${g.id}?departure=${departureId}`}
        viewTransition
        className="mt-5 inline-flex items-center gap-1 font-semibold text-brand-800 hover:text-brand-900"
      >
        Know your guide — {first}'s full profile and reviews →
      </Link>
    </section>
  )
}

/** Past this many characters a single-paragraph bio starts clamped to four lines. */
const LONG_BIO = 280

/**
 * The guide's own words to trekkers. A long bio opens on its first paragraph (at most four lines) with
 * "Read more", so the Book button and other dates stay close.
 */
function Bio({ text }: { text: string }) {
  const paragraphs = text.split(/\n\s*\n/).map((p) => p.trim()).filter(Boolean)
  const long = paragraphs.length > 1 || text.length > LONG_BIO
  const [open, setOpen] = useState(false)
  const shown = long && !open ? paragraphs.slice(0, 1) : paragraphs
  return (
    <div className="mt-4 space-y-3 text-stone-700">
      {shown.map((p, i) => (
        <p key={i} className={`whitespace-pre-line ${long && !open ? 'line-clamp-4' : ''}`}>
          {p}
        </p>
      ))}
      {long && (
        <button
          type="button"
          aria-expanded={open}
          onClick={() => setOpen(!open)}
          className="text-sm font-semibold text-brand-800 hover:text-brand-900"
        >
          {open ? 'Show less' : 'Read more'}
        </button>
      )}
    </div>
  )
}

/** The two IMF-recognised mountaineering courses, then any other certificate. */
const COURSES = [
  { key: 'bmc', name: 'Basic Mountaineering Course', short: 'BMC' },
  { key: 'amc', name: 'Advanced Mountaineering Course', short: 'AMC' },
] as const

/**
 * The page's most important card, so it's dressed to stand out: a dark header with a shield, then BMC and AMC as
 * rows (course, institute, certificate number), any other certificate, the year they started leading and their
 * languages. Blanks say "Not added yet". Carries its own heading.
 */
export function CredentialList({ guide: g }: { guide: GuideCredentials }) {
  const courses = COURSES.map((c) => ({
    ...c,
    institute: c.key === 'bmc' ? g.bmc_institute : g.amc_institute,
    number: c.key === 'bmc' ? g.bmc_certificate_number : g.amc_certificate_number,
  }))
  const rows: [string, string | null][] = [
    ...(g.certification ? [[g.certification, g.certification_number ?? 'Number not added yet'] as [string, string]] : []),
    ['Leading treks since', g.years_leading !== null ? `${new Date().getFullYear() - g.years_leading}` : null],
    ['Languages', g.languages],
  ]
  return (
    <section aria-labelledby="credentials-heading" className="overflow-hidden rounded-2xl bg-white shadow-sm ring-2 ring-brand-700">
      <div className="flex items-center gap-3 bg-brand-900 px-4 py-3.5 text-white sm:px-5">
        <svg viewBox="0 0 20 20" className="size-7 shrink-0 text-brand-200" fill="currentColor" aria-hidden="true">
          <path d="M10 1.5 3.5 4v5c0 4 2.8 7.6 6.5 9 3.7-1.4 6.5-5 6.5-9V4L10 1.5Zm-1 12L5.5 10l1.4-1.4L9 10.7l4.1-4.1 1.4 1.4L9 13.5Z" />
        </svg>
        <div className="min-w-0">
          <h2 id="credentials-heading" className="text-lg leading-tight font-semibold">Mountaineering credentials</h2>
          <p className="text-sm text-brand-100">Courses recognised by the Indian Mountaineering Foundation (IMF)</p>
        </div>
      </div>
      <table className="w-full text-left text-sm">
        <thead className="hidden text-[0.65rem] tracking-[0.12em] text-stone-500 uppercase sm:table-header-group">
          <tr className="border-b border-paper-200">
            <th scope="col" className="px-4 py-2.5 font-medium">Course</th>
            <th scope="col" className="px-4 py-2.5 font-medium">Institute</th>
            <th scope="col" className="px-4 py-2.5 font-medium">Certificate no.</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-paper-200">
          {courses.map((c) => (
            <tr key={c.key} className="grid gap-0.5 px-4 py-3 sm:table-row sm:p-0">
              <th scope="row" className="font-semibold text-stone-900 sm:px-4 sm:py-3 sm:align-top">
                <span className="inline-block rounded-md bg-brand-100 px-2 py-0.5 text-xs font-bold tracking-wide text-brand-900">{c.short}</span>
                <span className="mt-1 block text-xs font-normal text-stone-500">{c.name}</span>
              </th>
              <td className={`sm:px-4 sm:py-3 sm:align-top ${c.institute ? 'text-stone-800' : 'text-stone-500'}`}>
                {c.institute ?? 'Not added yet'}
              </td>
              <td className={`sm:px-4 sm:py-3 sm:align-top ${c.number ? 'font-medium text-stone-900' : 'text-stone-500'}`}>
                {c.number ? (
                  <>
                    <span className="font-normal text-stone-500 sm:hidden">No. </span>
                    {c.number}
                  </>
                ) : (
                  'Not added yet'
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <dl className="divide-y divide-paper-200 border-t border-paper-200 px-4">
        {rows.map(([label, value]) => (
          <div key={label} className="flex justify-between gap-4 py-3 text-sm">
            <dt className="text-stone-500">{label}</dt>
            <dd className={`text-right ${value ? 'font-medium text-stone-900' : 'text-stone-500'}`}>{value ?? 'Not added yet'}</dd>
          </div>
        ))}
      </dl>
    </section>
  )
}

/** Review cards: stars, the words (if any), who and which trek. */
export function ReviewList({ reviews }: { reviews: PublicReview[] }) {
  return (
    <ul className="mt-3 grid gap-3 sm:grid-cols-2">
      {reviews.map((r, i) => (
        <li key={i} className="rounded-xl bg-white p-4 ring-1 ring-paper-300">
          <p className="tracking-wider text-laterite-600" aria-label={`${r.rating} out of 5`}>
            {'★'.repeat(r.rating)}
            <span className="text-paper-300">{'★'.repeat(5 - r.rating)}</span>
          </p>
          {r.body && <p className="mt-2 text-stone-700">{r.body}</p>}
          <p className="mt-3 text-xs text-stone-500">
            {r.author_name} · {r.trek_name}, {parseDate(r.trek_start_date).toLocaleDateString('en-IN', { month: 'short', year: 'numeric' })}
          </p>
        </li>
      ))}
    </ul>
  )
}

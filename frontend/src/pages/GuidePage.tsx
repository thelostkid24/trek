import { useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { getGuide, guidePath, type DepartureSummary, type GuideProfile } from '../api/catalog.ts'
import { ApiError } from '../api/client.ts'
import { messageFor } from '../auth/errorMessages.ts'
import { useAuth } from '../auth/useAuth.ts'
import { Avatar } from '../components/Avatar.tsx'
import { RatingLine } from '../components/catalog/GuideProfileCard.tsx'
import { IconFacts, type Fact } from '../components/catalog/IconFacts.tsx'
import { AskForReviewButton } from '../components/guide/sections/AskForReviewButton.tsx'
import { AskQuestionCard } from '../components/guide/sections/AskQuestionCard.tsx'
import { AvailabilityLink } from '../components/guide/sections/AvailabilityLink.tsx'
import { CertificationsSection } from '../components/guide/sections/CertificationsSection.tsx'
import { FavouriteButton } from '../components/guide/sections/FavouriteButton.tsx'
import { MediaSection } from '../components/guide/sections/MediaSection.tsx'
import { ReplyTimeStat } from '../components/guide/sections/ReplyTimeStat.tsx'
import { ReviewsSection } from '../components/guide/sections/ReviewsSection.tsx'
import { TierBadge } from '../components/guide/sections/TierBadge.tsx'
import { rupees, shortRange, weekdaysAndYear } from '../lib/format.ts'
import { Seo } from '../components/Seo.tsx'
import { SITE_ORIGIN } from '../lib/siteLinks.ts'

/**
 * /guides/:id — public. "Know your guide": who they are, their credentials and record, what trekkers say, then the
 * dates they lead. Opened from a departure (`?departure=<id>`), that departure sits in a card on the right with its
 * Book button. `:id` may be the guide's slug, an old slug or their id; the address is swapped for the current slug.
 * Each part of the page from §7.21 is its own component in components/guide/sections. Contract: docs/TRD.md §7.7,
 * §7.21.
 */
export function GuidePage() {
  const { id = '' } = useParams()
  const [params] = useSearchParams()
  const guide = useQuery({
    queryKey: ['public-guide', id],
    queryFn: () => getGuide(id),
    retry: (count, error) => !(error instanceof ApiError && error.status === 404) && count < 2,
  })

  if (guide.isPending) {
    return (
      <div className="mx-auto max-w-4xl space-y-4 px-4 py-10" aria-busy="true" aria-label="Loading guide">
        <Seo title="Guide" />
        <div className="h-40 animate-pulse rounded-2xl bg-stone-200" />
        <div className="h-64 animate-pulse rounded-2xl bg-stone-100" />
      </div>
    )
  }
  if (guide.isError) {
    const missing = guide.error instanceof ApiError && guide.error.status === 404
    return (
      <section className="mx-auto max-w-md px-4 py-16 text-center">
        <Seo title="Guide not found" noindex />
        <h1 className="font-display text-2xl font-semibold">{missing ? 'Guide not found' : 'Something went wrong'}</h1>
        <p className="mt-2 text-stone-600">{missing ? 'This guide may no longer be leading treks.' : messageFor(guide.error)}</p>
        <Link to="/#treks" className="mt-4 inline-block text-brand-700 underline">
          See all treks
        </Link>
      </section>
    )
  }
  return <Profile guide={guide.data} departureId={params.get('departure')} />
}

/** Old links (an id or a slug the guide used before) end up on the current slug, keeping `?departure=`. */
function useCanonicalAddress(key: string, slug: string | null) {
  const navigate = useNavigate()
  const { search, hash } = useLocation()
  useEffect(() => {
    if (slug && key !== slug) navigate(`/guides/${slug}${search}${hash}`, { replace: true })
  }, [key, slug, search, hash, navigate])
}

function Profile({ guide: g, departureId }: { guide: GuideProfile; departureId: string | null }) {
  const { id: key = '' } = useParams()
  useCanonicalAddress(key, g.slug)
  const auth = useAuth()
  const owner = auth.status === 'authenticated' && auth.user.id === g.id
  const section = { guide: g, owner }
  const name = g.full_name ?? 'Local guide'
  const first = name.split(' ')[0]
  // The departure the trekker came from, if it's still listed.
  const booking = g.upcoming.find((d) => d.id === departureId) ?? null
  const stats: Fact[] = [
    ...(g.years_leading !== null
      ? [{ label: 'Leading treks', value: `${g.years_leading} ${g.years_leading === 1 ? 'year' : 'years'}`, icon: 'calendar' as const }]
      : []),
    { label: 'Treks led with us', value: String(g.treks_led), icon: 'mountain' },
    { label: 'Rating', value: g.rating !== null ? `${g.rating.toFixed(1)} of 5` : 'No reviews yet', icon: 'star' },
    ...(g.languages ? [{ label: 'Speaks', value: g.languages, icon: 'speech' as const }] : []),
  ]

  return (
    <div className="bg-paper-50">
      <Seo title={`${name}, trek guide`} description={g.bio ?? `${name} leads small-batch treks with The Empty Valley.`} path={guidePath(g)} />
      <div className="mx-auto max-w-5xl space-y-10 px-4 py-8 sm:py-12">
        <button type="button" onClick={() => window.history.back()} className="text-sm text-brand-700 hover:text-brand-900">
          ← Back
        </button>

        <header className="grid gap-6 sm:grid-cols-[auto_1fr] sm:items-center">
          {g.avatar_url ? (
            <img src={g.avatar_url} alt={name} className="h-44 w-36 rounded-2xl object-cover" />
          ) : (
            <Avatar url={null} name={name} size="lg" className="size-36! rounded-2xl! text-5xl!" />
          )}
          <div>
            <h1 className="font-serif text-5xl font-light leading-none tracking-tight text-stone-900">{name}</h1>
            <p className="mt-2 flex flex-wrap items-center gap-3">
              <RatingLine rating={g.rating} count={g.review_count} />
              <TierBadge {...section} />
            </p>
            <div className="mt-4 flex flex-wrap items-center gap-2">
              <ShareProfile name={name} url={`${SITE_ORIGIN}${guidePath(g)}`} />
              <FavouriteButton {...section} />
              {owner && (
                <>
                  <Link to="/guide/profile" className="rounded-full border border-paper-300 bg-white px-4 py-2 text-sm font-medium text-ink-900 hover:border-ink-900">
                    Edit profile
                  </Link>
                  <AskForReviewButton {...section} />
                </>
              )}
            </div>
          </div>
        </header>

        <div className="flex flex-wrap items-start gap-x-10 gap-y-4">
          <IconFacts facts={stats} className="flex flex-wrap gap-x-10 gap-y-4" />
          <ReplyTimeStat {...section} />
        </div>

        <MediaSection {...section} />

        <div className={booking ? 'grid gap-8 lg:grid-cols-[1fr_22rem] lg:items-start' : ''}>
          {booking && (
            <aside className="lg:sticky lg:top-20 lg:col-start-2 lg:row-start-1">
              <BookingCard departure={booking} />
            </aside>
          )}
          <div className="max-w-3xl min-w-0 space-y-8 lg:col-start-1 lg:row-start-1">
            <CertificationsSection {...section} />

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

            <ReviewsSection {...section} />

            <AskQuestionCard {...section} />

            <WalkWith first={first} upcoming={g.upcoming} />
            <AvailabilityLink {...section} />
          </div>
        </div>
      </div>
    </div>
  )
}

/**
 * The profile's public link, for the guide to share anywhere as their trekking CV. Phones get the native share sheet;
 * elsewhere (or if sharing isn't allowed) it copies the link.
 */
function ShareProfile({ name, url }: { name: string; url: string }) {
  const [copied, setCopied] = useState(false)
  const share = async () => {
    if (navigator.share) {
      try {
        await navigator.share({ title: `${name}, trek guide`, url })
        return
      } catch (e) {
        if (e instanceof DOMException && e.name === 'AbortError') return
      }
    }
    try {
      await navigator.clipboard.writeText(url)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 2000)
    } catch {
      window.prompt('Copy this link', url)
    }
  }
  return (
    <button
      type="button"
      onClick={() => void share()}
      className="inline-flex items-center gap-2 rounded-full border border-paper-300 bg-white px-4 py-2 text-sm font-medium text-ink-900 transition hover:border-ink-900"
    >
      <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.6" className="size-4" aria-hidden="true">
        <path d="M10 13V3m0 0L6.5 6.5M10 3l3.5 3.5M4 11v4.5A1.5 1.5 0 0 0 5.5 17h9a1.5 1.5 0 0 0 1.5-1.5V11" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
      <span aria-live="polite">{copied ? 'Link copied' : 'Share profile'}</span>
    </button>
  )
}

/** Every date the guide leads, behind a closed drop-down: seeing the other departures takes a tap. */
function WalkWith({ first, upcoming }: { first: string; upcoming: DepartureSummary[] }) {
  const [open, setOpen] = useState(false)
  return (
    <section className="rounded-xl bg-white ring-1 ring-paper-300">
      <button
        type="button"
        aria-expanded={open}
        onClick={() => setOpen(!open)}
        className="flex w-full items-center justify-between gap-3 px-4 py-3.5 text-left"
      >
        <span className="text-xs font-semibold tracking-[0.16em] text-laterite-600 uppercase">Walk with {first}</span>
        <span className="flex items-center gap-3 text-sm text-stone-500">
          {upcoming.length} {upcoming.length === 1 ? 'departure' : 'departures'}
          <svg viewBox="0 0 20 20" className={`size-4 text-stone-700 transition-transform ${open ? 'rotate-180' : ''}`} fill="currentColor" aria-hidden="true">
            <path d="M5.5 7.5 10 12l4.5-4.5z" />
          </svg>
        </span>
      </button>
      {open &&
        (upcoming.length === 0 ? (
          <p className="px-4 pb-4 text-sm text-stone-600">No upcoming departures right now.</p>
        ) : (
          <ul className="space-y-3 px-3 pb-3">
            {upcoming.map((d) => (
              <UpcomingRow key={d.id} departure={d} />
            ))}
          </ul>
        ))}
    </section>
  )
}

function UpcomingRow({ departure: d }: { departure: DepartureSummary }) {
  return (
    <li className="rounded-xl bg-white p-4 ring-1 ring-paper-300">
      <div className="flex items-baseline justify-between gap-3">
        <p className="font-semibold text-stone-900">{d.track.name}</p>
        <span className="font-semibold">{rupees(d.price_paise)}</span>
      </div>
      <p className="text-sm text-stone-600">
        {shortRange(d.start_date, d.end_date)} · {weekdaysAndYear(d.start_date, d.end_date)}
      </p>
      {d.bookable ? (
        <Link to={`/book/${d.id}`} className="mt-4 block rounded-full bg-brand-900 px-4 py-3 text-center font-semibold text-white hover:bg-brand-800">
          Book this departure
        </Link>
      ) : (
        <p className="mt-3 text-sm font-medium text-stone-500">{d.seats_left === 0 ? 'Batch full' : 'Bookings closed'}</p>
      )}
    </li>
  )
}

/** The departure the trekker was looking at, kept beside the guide's page so Book is one tap away. */
function BookingCard({ departure: d }: { departure: DepartureSummary }) {
  return (
    <section aria-label="Your departure" className="rounded-2xl bg-white p-5 ring-1 ring-paper-300 sm:p-6">
      <p className="text-xs font-semibold tracking-[0.16em] text-laterite-600 uppercase">Your departure</p>
      <p className="mt-2 font-serif text-2xl text-stone-900">{d.track.name}</p>
      <p className="text-stone-600">
        {shortRange(d.start_date, d.end_date)} · {weekdaysAndYear(d.start_date, d.end_date)}
      </p>
      <p className="mt-4">
        <span className="font-serif text-4xl font-light text-stone-900">{rupees(d.price_paise)}</span>
        <span className="text-stone-500"> per person</span>
      </p>
      {d.bookable ? (
        <Link to={`/book/${d.id}`} className="mt-5 block rounded-full bg-brand-900 px-4 py-3.5 text-center font-semibold text-white hover:bg-brand-800">
          Proceed with booking
        </Link>
      ) : (
        <p className="mt-4 text-sm font-medium text-stone-500">{d.seats_left === 0 ? 'Batch full' : 'Bookings closed'}</p>
      )}
      <Link to={`/departures/${d.id}`} viewTransition className="mt-3 block text-center text-sm text-brand-800 hover:text-brand-900">
        ← Back to these dates
      </Link>
    </section>
  )
}

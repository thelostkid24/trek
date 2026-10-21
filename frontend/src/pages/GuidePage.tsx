import { useQuery } from '@tanstack/react-query'
import { useEffect, useState, type ReactNode } from 'react'
import { Link, useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { getGuide, guideCardUrl, guidePath, type DepartureSummary, type GuideProfile } from '../api/catalog.ts'
import { ApiError } from '../api/client.ts'
import { messageFor } from '../auth/errorMessages.ts'
import { useAuth } from '../auth/useAuth.ts'
import { Avatar } from '../components/Avatar.tsx'
import { SectionLabel } from '../components/catalog/TrekSections.tsx'
import { AskForReviewButton } from '../components/guide/sections/AskForReviewButton.tsx'
import { AskQuestionCard } from '../components/guide/sections/AskQuestionCard.tsx'
import { AvailabilityLink } from '../components/guide/sections/AvailabilityLink.tsx'
import { CertificationsSection } from '../components/guide/sections/CertificationsSection.tsx'
import { FavouriteButton } from '../components/guide/sections/FavouriteButton.tsx'
import { MediaSection } from '../components/guide/sections/MediaSection.tsx'
import { ReplyTimeStat } from '../components/guide/sections/ReplyTimeStat.tsx'
import { ReviewsSection } from '../components/guide/sections/ReviewsSection.tsx'
import { TierBadge } from '../components/guide/sections/TierBadge.tsx'
import { feet, rupees, shortRange, weekdaysAndYear } from '../lib/format.ts'
import { Seo } from '../components/Seo.tsx'
import { SITE_ORIGIN } from '../lib/siteLinks.ts'

/**
 * /guides/:id — public. The guide's shareable profile: cover, who they are, their numbers, specialties, then "Know your
 * guide" (media, about, certifications, routes, career, reviews) beside a sticky column of upcoming departures, a
 * question box and the profile link. Opened from a departure (`?departure=<id>`), that departure sits in a card with its
 * Book button. `:id` may be the guide's slug, an old slug or their id; the address is swapped for the current slug.
 * The guide themself sees an owner bar and edit links. Each part of the page from §7.21 is its own component in
 * components/guide/sections. Contract: docs/TRD.md §7.7, §7.21.
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
      <div className="bg-paper-50" aria-busy="true" aria-label="Loading guide">
        <Seo title="Guide" />
        <div className="h-40 animate-pulse bg-stone-200 sm:h-56 lg:h-64" />
        <div className="mx-auto max-w-6xl space-y-4 px-4 py-8">
          <div className="h-24 animate-pulse rounded-2xl bg-stone-100" />
          <div className="h-64 animate-pulse rounded-2xl bg-stone-100" />
        </div>
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

const CARD = 'rounded-2xl bg-white p-5 ring-1 ring-paper-300 sm:p-6'
const OUTLINE_BUTTON =
  'inline-flex items-center gap-2 rounded-full border border-paper-300 bg-white px-4 py-2 text-sm font-medium text-ink-900 transition hover:border-ink-900'

function Profile({ guide: g, departureId }: { guide: GuideProfile; departureId: string | null }) {
  const { id: key = '' } = useParams()
  useCanonicalAddress(key, g.slug)
  const auth = useAuth()
  const owner = auth.status === 'authenticated' && auth.user.id === g.id
  const section = { guide: g, owner }
  const name = g.full_name ?? 'Local guide'
  const first = name.split(' ')[0]
  const url = `${SITE_ORIGIN}${guidePath(g)}`
  // The departure the trekker came from, if it's still listed.
  const booking = g.upcoming.find((d) => d.id === departureId) ?? null

  return (
    <div className="bg-paper-50 pb-16">
      <Seo
        title={`${name}, trek guide`}
        description={g.bio ?? `${name} leads small-batch treks with The Empty Valley.`}
        path={guidePath(g)}
        image={guideCardUrl(g)}
      />
      {owner && <OwnerBar section={section} first={first} name={name} url={url} />}
      <Cover url={g.cover_url} owner={owner} />

      <div className="mx-auto max-w-6xl px-4">
        <Header guide={g} owner={owner} name={name} url={url} />
        <Stats guide={g} owner={owner} />
        <Specialties items={g.specialties} owner={owner} />

        {booking && (
          <div className="mt-8 lg:hidden">
            <BookingCard departure={booking} />
          </div>
        )}

        <div className="mt-10 grid gap-10 lg:grid-cols-[minmax(0,1fr)_22rem] lg:items-start">
          <div className="min-w-0 space-y-10">
            <h2 className="font-serif text-3xl font-light tracking-tight text-stone-900 sm:text-4xl">Know your guide: {first}</h2>
            <MediaSection {...section} />
            <About bio={g.bio} first={first} owner={owner} />
            <CertificationsSection {...section} />
            <Routes treks={g.treks} first={first} />
            <Career milestones={g.milestones} owner={owner} />
            <ReviewsSection {...section} />
          </div>

          <aside className="space-y-5 lg:sticky lg:top-20">
            {booking && (
              <div className="hidden lg:block">
                <BookingCard departure={booking} />
              </div>
            )}
            <Departures section={section} first={first} />
            <AskQuestionCard {...section} />
            <ProfileLink guide={g} name={name} url={url} />
          </aside>
        </div>
      </div>
    </div>
  )
}

/** What only the guide sees, above their own page. */
function OwnerBar({ section, first, name, url }: { section: { guide: GuideProfile; owner: boolean }; first: string; name: string; url: string }) {
  return (
    <div className="border-b border-brand-800 bg-brand-900 text-white">
      <div className="mx-auto flex max-w-6xl flex-col gap-3 px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <p className="font-semibold">This is your public profile, {first}</p>
          <p className="text-sm text-brand-100">Only you see this bar. Trekkers see the page below it.</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <AskForReviewButton {...section} />
          <ShareProfile name={name} url={url} />
          <Link to="/guide/profile" className="rounded-full bg-white px-4 py-2 text-sm font-semibold text-brand-900 hover:bg-brand-50">
            Edit profile
          </Link>
        </div>
      </div>
    </div>
  )
}

/** The guide's 3:1 cover photo, or layered ridges in the brand greens. */
function Cover({ url, owner }: { url: string | null; owner: boolean }) {
  return (
    <div className="relative h-40 overflow-hidden bg-brand-900 sm:h-56 lg:h-72">
      {url ? <img src={url} alt="" className="size-full object-cover" /> : <Mountains />}
      {owner && (
        <Link
          to="/guide/profile"
          className="absolute right-4 bottom-3 rounded-full bg-black/45 px-3 py-1.5 text-xs font-medium text-white backdrop-blur hover:bg-black/60"
        >
          Change cover photo
        </Link>
      )}
    </div>
  )
}

function Mountains() {
  return (
    <svg viewBox="0 0 1440 400" preserveAspectRatio="xMidYMax slice" className="size-full" aria-hidden="true">
      <defs>
        <linearGradient id="guide-cover-sky" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#0b2118" />
          <stop offset="100%" stopColor="#245f42" />
        </linearGradient>
      </defs>
      <rect width="1440" height="400" fill="url(#guide-cover-sky)" />
      <path fill="#3f9468" fillOpacity="0.35" d="M0 230 L160 170 L300 210 L470 120 L640 200 L800 150 L980 210 L1160 130 L1320 190 L1440 160 L1440 400 L0 400 Z" />
      <path fill="#2f7a55" fillOpacity="0.6" d="M0 290 L200 240 L380 280 L560 220 L760 290 L940 240 L1120 285 L1300 235 L1440 270 L1440 400 L0 400 Z" />
      <path fill="#1b4a33" d="M0 350 L240 310 L470 345 L700 300 L930 350 L1150 315 L1440 345 L1440 400 L0 400 Z" />
    </svg>
  )
}

/** Photo over the cover's edge, "MOUNTAIN GUIDE · HOME", the name, tier and languages, their intro, save and share. */
function Header({ guide: g, owner, name, url }: { guide: GuideProfile; owner: boolean; name: string; url: string }) {
  const languages = (g.languages ?? '')
    .split(',')
    .map((l) => l.trim())
    .filter(Boolean)
  return (
    <header className="flex flex-col gap-5 sm:flex-row sm:items-end sm:justify-between">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:gap-6">
        <div className="-mt-14 shrink-0 sm:-mt-20">
          {g.avatar_url ? (
            <img
              src={g.avatar_url}
              alt={name}
              className="size-28 rounded-2xl object-cover ring-4 ring-paper-50 sm:size-40"
            />
          ) : (
            <Avatar url={null} name={name} size="lg" className="size-28! rounded-2xl! text-4xl! ring-4! ring-paper-50! sm:size-40! sm:text-5xl!" />
          )}
        </div>
        <div className="min-w-0">
          <p className="text-xs font-semibold tracking-[0.16em] text-laterite-600 uppercase">
            Mountain guide{g.home_city ? ` · ${g.home_city}` : ''}
          </p>
          <h1 className="mt-1 font-serif text-4xl leading-tight font-light tracking-tight text-stone-900 sm:text-5xl">{name}</h1>
          <div className="mt-3 flex flex-wrap items-center gap-2">
            <TierBadge guide={g} owner={owner} />
            {languages.map((l) => (
              <span key={l} className="rounded-full bg-paper-100 px-3 py-1 text-xs font-medium text-stone-700 ring-1 ring-paper-300">
                {l}
              </span>
            ))}
          </div>
          {g.quote && <p className="mt-3 max-w-2xl text-lg text-stone-700">{g.quote}</p>}
        </div>
      </div>
      <div className="flex shrink-0 flex-wrap items-center gap-2">
        <FavouriteButton guide={g} owner={owner} />
        <ShareProfile name={name} url={url} primary />
      </div>
    </header>
  )
}

/** The guide's record, worked out by the API at read time; a number with nothing behind it yet stays hidden. */
function Stats({ guide: g, owner }: { guide: GuideProfile; owner: boolean }) {
  const items: { value: ReactNode; label: string }[] = [
    ...(g.rating !== null
      ? [
          {
            value: (
              <>
                {g.rating.toFixed(1)}
                <span className="text-laterite-500" aria-hidden="true">
                  ★
                </span>
              </>
            ),
            label: `${g.review_count} ${g.review_count === 1 ? 'review' : 'reviews'}`,
          },
        ]
      : []),
    { value: g.treks_led, label: g.treks_led === 1 ? 'trek led' : 'treks led' },
    ...(g.years_leading !== null ? [{ value: g.years_leading, label: g.years_leading === 1 ? 'yr guiding' : 'yrs guiding' }] : []),
    ...(g.highest_altitude_m !== null ? [{ value: feet(g.highest_altitude_m), label: 'highest pass led' }] : []),
    ...(g.return_rate_pct !== null ? [{ value: `${g.return_rate_pct}%`, label: 'trekkers come back' }] : []),
  ]
  return (
    <section aria-label="Record" className="mt-8 border-y border-paper-300 py-5">
      <div className="grid grid-cols-2 gap-x-6 gap-y-5 sm:flex sm:flex-wrap sm:gap-x-10">
        {items.map((s) => (
          <p key={s.label}>
            <span className="block font-mono text-2xl text-stone-900">{s.value}</span>
            <span className="block text-sm text-stone-500">{s.label}</span>
          </p>
        ))}
        <ReplyTimeStat guide={g} owner={owner} />
      </div>
      {owner && <p className="mt-4 text-xs text-stone-500">These numbers update automatically from your treks and reviews.</p>}
    </section>
  )
}

function OwnerEdit({ children = 'Edit' }: { children?: ReactNode }) {
  return (
    <Link to="/guide/profile" className="text-xs font-medium tracking-normal text-brand-700 normal-case hover:text-brand-900">
      {children}
    </Link>
  )
}

function Specialties({ items, owner }: { items: string[]; owner: boolean }) {
  if (items.length === 0 && !owner) return null
  return (
    <section aria-labelledby="specialties" className="mt-6">
      <SectionLabel id="specialties" aside={owner ? <OwnerEdit /> : undefined}>
        Specialties
      </SectionLabel>
      {items.length === 0 ? (
        <p className="mt-3 text-sm text-stone-500">Pick what you're known for in your profile.</p>
      ) : (
        <ul className="mt-3 flex flex-wrap gap-2">
          {items.map((s) => (
            <li key={s} className="rounded-full bg-brand-50 px-3 py-1.5 text-sm font-medium text-brand-800 ring-1 ring-brand-100">
              {s}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

/** The guide's bio in their own words, paragraph by paragraph. */
function About({ bio, first, owner }: { bio: string | null; first: string; owner: boolean }) {
  if (!bio && !owner) return null
  return (
    <section aria-labelledby="about" className={CARD}>
      <SectionLabel id="about" aside={owner ? <OwnerEdit /> : undefined}>
        About
      </SectionLabel>
      {bio ? (
        <>
          <div className="mt-4 space-y-4 text-stone-700">
            {bio.split(/\n\s*\n/).map((p, i) => (
              <p key={i} className="whitespace-pre-line">
                {p}
              </p>
            ))}
          </div>
          <p className="mt-4 text-sm text-stone-500">Written by {first}</p>
        </>
      ) : (
        <p className="mt-4 text-sm text-stone-500">Tell trekkers who you are and how you lead. Add your story in your profile.</p>
      )}
    </section>
  )
}

/** Every trek they've led, most first; the picked one shows its top, times led, season and the guide's note. */
function Routes({ treks, first }: { treks: GuideProfile['treks']; first: string }) {
  const [picked, setPicked] = useState(0)
  if (treks.length === 0) return null
  const t = treks[Math.min(picked, treks.length - 1)]
  return (
    <section aria-labelledby="routes">
      <h2 id="routes" className="font-serif text-2xl font-light text-stone-900">
        Routes {first} leads
      </h2>
      <div className="mt-4 grid gap-4 sm:grid-cols-[minmax(0,14rem)_1fr]">
        <ul className="flex gap-2 overflow-x-auto pb-1 sm:flex-col sm:overflow-visible sm:pb-0">
          {treks.map((r, i) => (
            <li key={r.track.slug} className="shrink-0">
              <button
                type="button"
                aria-pressed={r === t}
                onClick={() => setPicked(i)}
                className={`flex w-full items-baseline justify-between gap-3 rounded-xl px-4 py-2.5 text-left text-sm transition ${
                  r === t ? 'bg-brand-900 text-white' : 'bg-white text-stone-800 ring-1 ring-paper-300 hover:ring-stone-400'
                }`}
              >
                <span className="font-medium">{r.track.name}</span>
                <span className={`font-mono text-xs ${r === t ? 'text-brand-100' : 'text-stone-500'}`}>{r.times}×</span>
              </button>
            </li>
          ))}
        </ul>
        <div className={CARD}>
          <p className="font-serif text-2xl text-stone-900">{t.track.name}</p>
          <p className="text-sm text-stone-500">{t.track.region}</p>
          <dl className="mt-4 grid grid-cols-3 gap-4">
            <RouteFact label="Top" value={t.max_altitude_m !== null ? feet(t.max_altitude_m) : '—'} />
            <RouteFact label="Led" value={`${t.times} ${t.times === 1 ? 'time' : 'times'}`} />
            <RouteFact label="Season" value={t.season_label ?? '—'} />
          </dl>
          {t.note && (
            <blockquote className="mt-5 border-l-2 border-laterite-400 pl-4 text-stone-700 italic">
              “{t.note}” <span className="not-italic text-stone-500">— {first}</span>
            </blockquote>
          )}
          <Link to={`/treks/${t.track.slug}`} className="mt-5 inline-block text-sm font-medium text-brand-700 hover:text-brand-900">
            See the {t.track.name} trek →
          </Link>
        </div>
      </div>
    </section>
  )
}

function RouteFact({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-[0.65rem] font-semibold tracking-[0.16em] text-stone-500 uppercase">{label}</dt>
      <dd className="mt-1 font-mono text-sm text-stone-900">{value}</dd>
    </div>
  )
}

function Career({ milestones, owner }: { milestones: GuideProfile['milestones']; owner: boolean }) {
  if (milestones.length === 0 && !owner) return null
  return (
    <section aria-labelledby="career">
      <div className="flex items-baseline justify-between gap-3">
        <h2 id="career" className="font-serif text-2xl font-light text-stone-900">
          Career in the mountains
        </h2>
        {owner && <OwnerEdit>+ Add milestone</OwnerEdit>}
      </div>
      {milestones.length === 0 ? (
        <p className="mt-3 text-sm text-stone-500">Courses, first summits, your hundredth trek: add the years that shaped you.</p>
      ) : (
        <ol className="mt-4 divide-y divide-paper-300 border-y border-paper-300">
          {milestones.map((m, i) => (
            <li key={i} className="grid grid-cols-[4rem_1fr] gap-4 py-3">
              <span className="font-mono text-sm text-laterite-600">{m.year}</span>
              <span className="text-stone-800">{m.text}</span>
            </li>
          ))}
        </ol>
      )}
    </section>
  )
}

/** Every date the guide leads. Open seat counts are never shown (§7.21 decisions), only "Batch full". */
function Departures({ section, first }: { section: { guide: GuideProfile; owner: boolean }; first: string }) {
  const upcoming = section.guide.upcoming
  const next = upcoming.find((d) => d.bookable)
  return (
    <section aria-labelledby="departures" className={CARD}>
      <AvailabilityLink {...section} />
      <SectionLabel id="departures">Upcoming departures</SectionLabel>
      {upcoming.length === 0 ? (
        <p className="mt-3 text-sm text-stone-600">No upcoming departures with {first} right now.</p>
      ) : (
        <ul className="mt-3 divide-y divide-paper-300">
          {upcoming.map((d) => (
            <UpcomingRow key={d.id} departure={d} />
          ))}
        </ul>
      )}
      {next && (
        <Link
          to={`/book/${next.id}`}
          className="mt-4 block rounded-full bg-brand-900 px-4 py-3 text-center font-semibold text-white hover:bg-brand-800"
        >
          Join {shortRange(next.start_date, next.end_date)}
        </Link>
      )}
    </section>
  )
}

function UpcomingRow({ departure: d }: { departure: DepartureSummary }) {
  return (
    <li className="py-3">
      <div className="flex items-baseline justify-between gap-3">
        <Link to={`/departures/${d.id}`} viewTransition className="font-medium text-stone-900 hover:text-brand-800">
          {d.track.name}
        </Link>
        <span className="font-mono text-sm text-stone-900">{rupees(d.price_paise)}</span>
      </div>
      <div className="mt-0.5 flex items-baseline justify-between gap-3 text-sm">
        <span className="text-stone-600">
          {shortRange(d.start_date, d.end_date)} · {weekdaysAndYear(d.start_date, d.end_date)}
        </span>
        {d.bookable ? (
          <Link to={`/book/${d.id}`} className="shrink-0 font-medium text-brand-700 hover:text-brand-900">
            Book
          </Link>
        ) : (
          <span className="shrink-0 text-stone-500">{d.seats_left === 0 ? 'Batch full' : 'Bookings closed'}</span>
        )}
      </div>
    </li>
  )
}

/** The departure the trekker was looking at, kept beside the guide's page so Book is one tap away. */
function BookingCard({ departure: d }: { departure: DepartureSummary }) {
  return (
    <section aria-label="Your departure" className={CARD}>
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

/** The page's address, for the guide (or anyone) to pass on. */
function ProfileLink({ guide: g, name, url }: { guide: GuideProfile; name: string; url: string }) {
  return (
    <section aria-labelledby="profile-link" className={CARD}>
      <SectionLabel id="profile-link">Profile link</SectionLabel>
      <p className="mt-3 truncate font-mono text-sm text-stone-800">{`${SITE_ORIGIN.replace(/^https?:\/\//, '')}${guidePath(g)}`}</p>
      <div className="mt-4">
        <ShareProfile name={name} url={url} />
      </div>
    </section>
  )
}

/**
 * The profile's public link, for the guide to share anywhere as their trekking CV. Phones get the native share sheet;
 * elsewhere (or if sharing isn't allowed) it copies the link.
 */
function ShareProfile({ name, url, primary = false }: { name: string; url: string; primary?: boolean }) {
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
      className={
        primary
          ? 'inline-flex items-center gap-2 rounded-full bg-brand-900 px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-brand-800'
          : OUTLINE_BUTTON
      }
    >
      <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.6" className="size-4" aria-hidden="true">
        <path d="M10 13V3m0 0L6.5 6.5M10 3l3.5 3.5M4 11v4.5A1.5 1.5 0 0 0 5.5 17h9a1.5 1.5 0 0 0 1.5-1.5V11" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
      <span aria-live="polite">{copied ? 'Link copied' : 'Share profile'}</span>
    </button>
  )
}

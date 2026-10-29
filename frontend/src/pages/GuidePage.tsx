import { useQuery } from '@tanstack/react-query'
import { useEffect, useRef, useState, type ReactNode } from 'react'
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
import { lastLed, milestoneDate } from '../lib/guideRecord.ts'
import { isNewHere } from '../lib/trek.ts'
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
        description={g.quote ?? g.bio ?? `${name} leads small-batch treks with The Empty Valley.`}
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
            <MediaSection {...section} />
            <About bio={g.bio} first={first} owner={owner} />
            <CertificationsSection {...section} />
            <Routes treks={g.treks} first={first} />
            <Career milestones={g.milestones} owner={owner} />
            <ReviewsSection {...section} />
          </div>

          <aside className="space-y-5 lg:sticky lg:top-[calc(var(--header-h)+1rem)]">
            {booking && (
              <div className="hidden lg:block">
                <BookingCard departure={booking} />
              </div>
            )}
            <Departures section={section} first={first} />
            <AskQuestionCard {...section} />
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
      {/* Top-aligned, so the photo's negative margin lifts it over the cover's edge (with items-end it wouldn't). */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:gap-6">
        <div className="relative -mt-14 flex shrink-0 flex-col items-center sm:-mt-20">
          {g.avatar_url ? (
            <PhotoViewer url={g.avatar_url} name={name} />
          ) : (
            <Avatar url={null} name={name} size="lg" className="size-28! rounded-full! text-4xl! ring-4! ring-paper-50! sm:size-40! sm:text-5xl!" />
          )}
          {isNewHere(g) && (
            <>
              {/* Positioned so it stays over the photo, whose hover filter would otherwise paint on top of it. */}
              <span className="relative z-10 -mt-4 rounded-full bg-brand-900 px-3 py-1 text-[11px] font-bold tracking-[0.12em] text-white uppercase ring-2 ring-paper-50">
                New here
              </span>
              <span className="mt-1.5 text-xs text-stone-500">Joined The Empty Valley in {joinedFormat.format(new Date(g.joined_at))}</span>
            </>
          )}
        </div>
        <div className="min-w-0 sm:pt-5">
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

const joinedFormat = new Intl.DateTimeFormat('en-IN', { month: 'short', year: 'numeric' })

/** The profile photo; a click opens it full size over the page. Escape or the close button shuts it. */
function PhotoViewer({ url, name }: { url: string; name: string }) {
  const dialog = useRef<HTMLDialogElement>(null)
  return (
    <>
      <button
        type="button"
        onClick={() => dialog.current?.showModal()}
        aria-label={`Open ${name}'s photo`}
        className="cursor-zoom-in rounded-full transition hover:brightness-95 focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-brand-600"
      >
        <img src={url} alt={name} className="size-28 rounded-full object-cover ring-4 ring-paper-50 sm:size-40" />
      </button>
      <dialog
        ref={dialog}
        aria-label={`${name}'s photo`}
        className="m-auto max-h-[90dvh] max-w-[min(90vw,48rem)] overflow-visible bg-transparent p-0 backdrop:bg-black/80"
      >
        <img src={url} alt={name} className="max-h-[90dvh] w-auto rounded-2xl object-contain" />
        <button
          type="button"
          onClick={() => dialog.current?.close()}
          aria-label="Close photo"
          className="absolute -top-3 -right-3 flex size-9 items-center justify-center rounded-full bg-white text-stone-900 shadow-lg hover:bg-stone-100"
        >
          <svg viewBox="0 0 24 24" className="size-4" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
            <path d="M6 6l12 12M18 6 6 18" />
          </svg>
        </button>
      </dialog>
    </>
  )
}

/** The guide's record, worked out by the API at read time; a number with nothing behind it yet stays hidden. */
/** The numbers an admin picked (§7.21.8); without picks, the default row and the reply time. */
function Stats({ guide: g, owner }: { guide: GuideProfile; owner: boolean }) {
  const items: { value: ReactNode; label: string }[] = g.stats ?? [
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
        {items.map((s, i) => (
          <p key={i}>
            <span className="block font-mono text-2xl text-stone-900">{s.value}</span>
            <span className="block text-sm text-stone-500">{s.label}</span>
          </p>
        ))}
        {g.stats === null && <ReplyTimeStat guide={g} owner={owner} />}
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

/**
 * Their routes in the kept order, then treks completed with us; the picked one shows times led, last led, a link to the
 * trek's published dates, the top, the guide's note and the trek's page (§7.21.8).
 */
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
            <li key={r.route_id ?? r.track?.slug ?? r.name} className="shrink-0">
              <button
                type="button"
                aria-pressed={r === t}
                onClick={() => setPicked(i)}
                className={`flex w-full items-baseline justify-between gap-3 rounded-xl px-4 py-2.5 text-left text-sm transition ${
                  r === t ? 'bg-brand-900 text-white' : 'bg-white text-stone-800 ring-1 ring-paper-300 hover:ring-stone-400'
                }`}
              >
                <span className="font-medium">{r.name}</span>
                <span className={`font-mono text-xs ${r === t ? 'text-brand-100' : 'text-stone-500'}`}>{r.times !== null ? `${r.times}×` : 'Led'}</span>
              </button>
            </li>
          ))}
        </ul>
        <div className={CARD}>
          <p className="font-serif text-2xl text-stone-900">{t.name}</p>
          {t.track && <p className="text-sm text-stone-500">{t.track.region}</p>}
          <dl className="mt-4 grid grid-cols-2 gap-4 sm:grid-cols-3">
            <RouteFact label="Times led" value={t.times !== null ? `${t.times} ${t.times === 1 ? 'time' : 'times'}` : 'Not given'} />
            <RouteFact label="Last led" value={lastLed(t)} />
            <RouteFact
              label="Available"
              value={
                <Link to={t.track ? `/treks/${t.track.slug}` : '/treks'} className="text-brand-700 hover:text-brand-900">
                  As per dates published
                </Link>
              }
            />
            <RouteFact label="Top" value={t.max_altitude_m !== null ? feet(t.max_altitude_m) : 'Not given'} />
          </dl>
          {t.note && (
            <blockquote className="mt-5 border-l-2 border-laterite-400 pl-4 text-stone-700 italic">
              “{t.note}” <span className="not-italic text-stone-500">— {first}</span>
            </blockquote>
          )}
          <Link to={t.track ? `/treks/${t.track.slug}` : '/treks'} className="mt-5 inline-block text-sm font-medium text-brand-700 hover:text-brand-900">
            {t.track ? `See the ${t.track.name} trek →` : 'See the related treks →'}
          </Link>
        </div>
      </div>
    </section>
  )
}

function RouteFact({ label, value }: { label: string; value: ReactNode }) {
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
            <li key={i} className="grid grid-cols-[6.5rem_1fr] gap-4 py-3">
              <span className="font-mono text-sm text-laterite-600">{milestoneDate(m)}</span>
              <span className="text-stone-800">{m.text}</span>
            </li>
          ))}
        </ol>
      )}
    </section>
  )
}

/**
 * Every date the guide leads, grouped by trek in date order: the trek's price, length and top, a link to the trek, then
 * one row per date with Book. Open seat counts are never shown (§7.21 decisions), only "Batch full".
 */
function Departures({ section, first }: { section: { guide: GuideProfile; owner: boolean }; first: string }) {
  const groups: { track: DepartureSummary['track']; dates: DepartureSummary[] }[] = []
  for (const d of [...section.guide.upcoming].sort((a, b) => a.start_date.localeCompare(b.start_date))) {
    const group = groups.find((g) => g.track.slug === d.track.slug)
    if (group) group.dates.push(d)
    else groups.push({ track: d.track, dates: [d] })
  }
  return (
    // The one place to book from this page, so it stands out from the plain cards around it.
    <section aria-labelledby="departures" className="rounded-2xl bg-brand-50 p-5 shadow-lg shadow-brand-900/10 ring-2 ring-brand-700 sm:p-6">
      <AvailabilityLink {...section} />
      <SectionLabel id="departures">Upcoming departures</SectionLabel>
      {groups.length === 0 ? (
        <p className="mt-3 text-sm text-stone-600">No upcoming departures with {first} right now.</p>
      ) : (
        <div className="mt-4 space-y-7">
          {groups.map((g) => (
            <TrekDates
              key={g.track.slug}
              track={g.track}
              dates={g.dates}
              topM={section.guide.treks.find((t) => t.track?.slug === g.track.slug)?.max_altitude_m ?? null}
            />
          ))}
        </div>
      )}
    </section>
  )
}

function TrekDates({ track, dates, topM }: { track: DepartureSummary['track']; dates: DepartureSummary[]; topM: number | null }) {
  const prices = dates.map((d) => d.price_paise)
  const low = Math.min(...prices)
  const facts = [`${track.duration_days} ${track.duration_days === 1 ? 'day' : 'days'}`, topM !== null && feet(topM)].filter(Boolean)
  return (
    <div>
      <div className="flex items-start justify-between gap-3">
        <h3 className="text-xl font-medium text-stone-900">{track.name}</h3>
        <p className="shrink-0 text-right">
          <span className="font-mono text-lg font-semibold text-stone-900">
            {prices.some((p) => p !== low) && <span className="text-sm font-normal text-stone-500">from </span>}
            {rupees(low)}
          </span>
          <span className="block text-xs text-stone-500">per person</span>
        </p>
      </div>
      <p className="mt-1 text-sm text-stone-600">{facts.join(' · ')}</p>
      <Link to={`/treks/${track.slug}`} viewTransition className="mt-2 inline-block text-sm font-semibold text-brand-800 hover:text-brand-900">
        View {track.name} trek →
      </Link>
      <ul className="mt-2">
        {dates.map((d) => (
          <li key={d.id} className="flex items-center justify-between gap-3 py-1">
            <span className="text-base text-stone-900">{shortRange(d.start_date, d.end_date)}</span>
            {d.bookable ? (
              <Link to={`/book/${d.id}`} className="shrink-0 rounded-full bg-brand-900 px-4 py-1.5 text-sm font-semibold text-white transition hover:bg-brand-950">
                Book
              </Link>
            ) : (
              <span className="shrink-0 text-sm text-stone-500">{d.seats_left === 0 ? 'Batch full' : 'Bookings closed'}</span>
            )}
          </li>
        ))}
      </ul>
    </div>
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

/**
 * The profile's public link, for the guide to share anywhere as their trekking CV: a menu of LinkedIn, Instagram (copies
 * the link, then opens Instagram), Facebook, X, Email, the device's own share sheet ("Share via…", where the
 * browser has one) and Copy link.
 */
function ShareProfile({ name, url, primary = false }: { name: string; url: string; primary?: boolean }) {
  const [open, setOpen] = useState(false)
  const [copied, setCopied] = useState(false)
  const box = useRef<HTMLDivElement>(null)
  const title = `${name}, trek guide`
  const u = encodeURIComponent(url)
  const t = encodeURIComponent(`${title} on The Empty Valley`)

  // Closes on Escape or a click outside.
  useEffect(() => {
    if (!open) return
    const onDown = (e: MouseEvent) => {
      if (!box.current?.contains(e.target as Node)) setOpen(false)
    }
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  const copy = async () => {
    setOpen(false)
    try {
      await navigator.clipboard.writeText(url)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 2000)
    } catch {
      window.prompt('Copy this link', url)
    }
  }
  const shareVia = async () => {
    setOpen(false)
    try {
      await navigator.share({ title, url })
    } catch {
      // Cancelled, or not allowed here: nothing to do.
    }
  }

  const item = 'flex w-full items-center gap-3 rounded-lg px-3 py-2 text-left text-sm text-stone-800 hover:bg-paper-100'
  // Instagram has no share link: copy the profile link, then open Instagram to paste it into a story, post or bio.
  const instagram = () => {
    setOpen(false)
    const copying = navigator.clipboard?.writeText(url)
    window.open('https://www.instagram.com/', '_blank', 'noopener,noreferrer')
    void copying?.then(
      () => {
        setCopied(true)
        window.setTimeout(() => setCopied(false), 2000)
      },
      () => {},
    )
  }

  const linkedin = { label: 'LinkedIn', href: `https://www.linkedin.com/sharing/share-offsite/?url=${u}`, icon: <LinkedInIcon /> }
  const links = [
    { label: 'Facebook', href: `https://www.facebook.com/sharer/sharer.php?u=${u}`, icon: <FacebookIcon /> },
    { label: 'X', href: `https://twitter.com/intent/tweet?url=${u}&text=${t}`, icon: <XIcon /> },
  ]
  return (
    <div ref={box} className="relative">
      <button
        type="button"
        aria-expanded={open}
        onClick={() => setOpen((o) => !o)}
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
      {open && (
        <div className="absolute right-0 z-30 mt-2 w-52 rounded-xl bg-white p-1.5 shadow-lg ring-1 ring-paper-300">
          <a href={linkedin.href} target="_blank" rel="noopener noreferrer" onClick={() => setOpen(false)} className={item}>
            {linkedin.icon}
            {linkedin.label}
          </a>
          <button type="button" onClick={instagram} className={item}>
            <InstagramIcon />
            Instagram
          </button>
          {links.map((l) => (
            <a key={l.label} href={l.href} target="_blank" rel="noopener noreferrer" onClick={() => setOpen(false)} className={item}>
              {l.icon}
              {l.label}
            </a>
          ))}
          <a href={`mailto:?subject=${encodeURIComponent(title)}&body=${u}`} onClick={() => setOpen(false)} className={item}>
            <MailIcon />
            Email
          </a>
          {'share' in navigator && (
            <button type="button" onClick={() => void shareVia()} className={item}>
              <ShareArrowIcon />
              Share via…
            </button>
          )}
          <div className="my-1.5 border-t border-paper-300" />
          <button type="button" onClick={() => void copy()} className={item}>
            <LinkIcon />
            Copy link
          </button>
        </div>
      )}
    </div>
  )
}

const ICON = 'size-[18px] shrink-0'

// Brand marks in their own colours (shapes from Simple Icons); the rest are thin outline icons.
function FacebookIcon() {
  return (
    <svg viewBox="0 0 24 24" className={ICON} fill="#0866FF" aria-hidden="true">
      <path d="M9.1 23.7v-8H6.6V12h2.5v-1.6c0-4.1 1.8-6 5.9-6l2.6.3v3.3h-1.4c-1.4 0-1.9.5-1.9 1.9V12h3.8l-.6 3.7h-3.2v8.2A12 12 0 1 0 9.1 23.7z" />
    </svg>
  )
}

function LinkedInIcon() {
  return (
    <svg viewBox="0 0 24 24" className={ICON} fill="#0A66C2" aria-hidden="true">
      <path d="M20.45 20.45h-3.56v-5.57c0-1.33-.02-3.04-1.85-3.04-1.85 0-2.14 1.45-2.14 2.94v5.67H9.35V9h3.41v1.56h.05c.48-.9 1.64-1.85 3.37-1.85 3.6 0 4.27 2.37 4.27 5.46zM5.34 7.43a2.06 2.06 0 1 1 0-4.13 2.06 2.06 0 0 1 0 4.13zm1.78 13.02H3.56V9h3.56zM22.23 0H1.77C.79 0 0 .77 0 1.73v20.54C0 23.23.79 24 1.77 24h20.45c.98 0 1.78-.77 1.78-1.73V1.73C24 .77 23.2 0 22.22 0z" />
    </svg>
  )
}

function InstagramIcon() {
  return (
    <svg viewBox="0 0 24 24" className={ICON} fill="none" stroke="url(#ig-share)" strokeWidth="2.2" strokeLinecap="round" aria-hidden="true">
      <defs>
        <linearGradient id="ig-share" x1="0" y1="1" x2="1" y2="0">
          <stop offset="0" stopColor="#FEDA75" />
          <stop offset=".3" stopColor="#FA7E1E" />
          <stop offset=".6" stopColor="#D62976" />
          <stop offset="1" stopColor="#4F5BD5" />
        </linearGradient>
      </defs>
      <rect width="19" height="19" x="2.5" y="2.5" rx="5.5" />
      <circle cx="12" cy="12" r="4.2" />
      <path d="M17.4 6.6h.01" strokeWidth="2.8" />
    </svg>
  )
}

function XIcon() {
  return (
    <svg viewBox="0 0 24 24" className={`${ICON} text-stone-900`} fill="currentColor" aria-hidden="true">
      <path d="M18.9 1.15h3.68l-8.04 9.19L24 22.85h-7.4l-5.8-7.58-6.64 7.58H.47l8.6-9.83L0 1.15h7.6l5.24 6.93zm-1.29 19.5h2.04L6.49 3.24H4.3z" />
    </svg>
  )
}

const OUTLINE_ICON = {
  viewBox: '0 0 24 24',
  className: `${ICON} text-stone-600`,
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.8,
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const,
  'aria-hidden': true,
}

function MailIcon() {
  return (
    <svg {...OUTLINE_ICON}>
      <rect width="20" height="16" x="2" y="4" rx="2" />
      <path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7" />
    </svg>
  )
}

function ShareArrowIcon() {
  return (
    <svg {...OUTLINE_ICON}>
      <path d="M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8M16 6l-4-4-4 4M12 2v13" />
    </svg>
  )
}

function LinkIcon() {
  return (
    <svg {...OUTLINE_ICON}>
      <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71" />
      <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71" />
    </svg>
  )
}

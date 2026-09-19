import { useQuery } from '@tanstack/react-query'
import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { DIFFICULTY_LABEL, listDepartures, type TrackBrief } from '../api/catalog.ts'
import { messageFor } from '../auth/errorMessages.ts'
import { FaqList } from '../components/FaqList.tsx'
import { FAQS } from '../lib/faqs.ts'
import { Ridgeline } from '../components/Ridgeline.tsx'
import { SITE_LINKS } from '../lib/siteLinks.ts'

export function HomePage() {
  return (
    <div className="font-plex text-ink-900">
      <Hero />
      <Routes />
      <HowItWorks />
      <Faq />
    </div>
  )
}

function Container({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <div className={`mx-auto max-w-[90rem] px-5 sm:px-10 ${className}`}>{children}</div>
}

function Hero() {
  return (
    <section className="relative isolate flex min-h-[32rem] items-end overflow-hidden bg-ink-900 text-white sm:min-h-[36rem]">
      {/* Ridgeline art until a photo is dropped at public/hero.jpg, which then covers it. */}
      <Ridgeline className="absolute inset-0 -z-30 size-full" />
      <div className="absolute inset-0 -z-20 bg-[url('/hero.jpg')] bg-cover bg-center" aria-hidden="true" />
      <div
        className="absolute inset-0 -z-10 bg-gradient-to-r from-ink-950/80 via-ink-950/35 to-transparent"
        aria-hidden="true"
      />
      <div className="absolute inset-x-0 bottom-0 -z-10 h-1/2 bg-gradient-to-t from-ink-950/60 to-transparent" aria-hidden="true" />
      <Container className="w-full pt-24 pb-16 sm:pb-20">
        <h1 className="max-w-2xl font-serif text-4xl leading-[1.15] tracking-tight sm:text-[3.4rem]">
          Know your guide before you meet the mountain.
        </h1>
        <a
          href="#treks"
          className="mt-8 inline-block rounded-sm bg-pine-600 px-6 py-3.5 text-sm font-medium text-white hover:bg-pine-700"
        >
          Browse treks
        </a>
      </Container>
    </section>
  )
}

/** One card per route that has a live departure, in listing order. */
function Routes() {
  const departures = useQuery({ queryKey: ['public-departures'], queryFn: listDepartures })
  const tracks = [...new Map((departures.data?.items ?? []).map((d) => [d.track.slug, d.track])).values()]
  const rail = useRef<HTMLUListElement>(null)
  const [edges, setEdges] = useState({ start: true, end: true })

  const updateEdges = () => {
    const el = rail.current
    if (!el) return
    setEdges({ start: el.scrollLeft <= 1, end: el.scrollLeft + el.clientWidth >= el.scrollWidth - 1 })
  }
  useEffect(() => {
    updateEdges()
    window.addEventListener('resize', updateEdges)
    return () => window.removeEventListener('resize', updateEdges)
  }, [tracks.length])

  const scroll = (dir: 1 | -1) => {
    const el = rail.current
    const card = el?.firstElementChild as HTMLElement | null
    if (el && card) el.scrollBy({ left: dir * (card.offsetWidth + 24), behavior: 'smooth' })
  }

  return (
    <section id="treks" className="scroll-mt-16 bg-paper-100 py-16 sm:py-20">
      <Container>
        <div className="flex items-end justify-between gap-6">
          <SectionHeading
            title={tracks.length > 0 ? `Live on ${tracks.length} ${tracks.length === 1 ? 'route' : 'routes'}` : 'Our routes'}
            sub="Small batches out of the Garhwal and Kumaon valleys."
          />
          {tracks.length > 1 && (
            <div className="hidden shrink-0 gap-3 sm:flex">
              <ArrowButton label="Previous routes" disabled={edges.start} onClick={() => scroll(-1)}>
                ←
              </ArrowButton>
              <ArrowButton label="Next routes" disabled={edges.end} onClick={() => scroll(1)}>
                →
              </ArrowButton>
            </div>
          )}
        </div>

        {departures.isPending ? (
          <ul className="mt-8 flex gap-6 overflow-hidden" aria-busy="true" aria-label="Loading routes">
            {[0, 1, 2, 3].map((i) => (
              <li key={i} className="h-[26rem] w-[18.75rem] shrink-0 animate-pulse rounded-sm bg-paper-200" />
            ))}
          </ul>
        ) : departures.isError ? (
          <div className="mt-8 rounded-sm border border-paper-300 bg-paper-50 p-6 text-center">
            <p className="text-ink-700">{messageFor(departures.error)}</p>
            <button
              type="button"
              onClick={() => void departures.refetch()}
              className="mt-3 rounded-sm bg-pine-600 px-5 py-2 text-sm font-medium text-white hover:bg-pine-700"
            >
              Try again
            </button>
          </div>
        ) : tracks.length === 0 ? (
          <div className="mt-8 rounded-sm border border-paper-300 bg-paper-50 p-8 text-center">
            <p className="font-serif text-xl text-ink-900">New departures are on the way</p>
            <p className="mt-1 text-sm text-ink-700/75">Our guides are planning the next batches. Check back soon.</p>
          </div>
        ) : (
          <ul
            ref={rail}
            onScroll={updateEdges}
            className="-mx-5 mt-8 flex snap-x snap-mandatory scroll-px-5 gap-6 overflow-x-auto px-5 pb-2 [scrollbar-width:none] sm:-mx-10 sm:scroll-px-10 sm:px-10 [&::-webkit-scrollbar]:hidden"
          >
            {tracks.map((t) => (
              <RouteCard key={t.slug} track={t} />
            ))}
          </ul>
        )}
      </Container>
    </section>
  )
}

function RouteCard({ track }: { track: TrackBrief }) {
  return (
    <li className="relative flex w-[78vw] max-w-[18.75rem] shrink-0 snap-start flex-col overflow-hidden rounded-sm border border-paper-300 bg-paper-50 sm:w-[18.75rem]">
      {/* Route photos aren't in the catalog yet; the name stands in. */}
      <div className="flex aspect-[4/3] flex-col items-center justify-center gap-2 bg-ink-800 text-ink-400">
        <svg viewBox="0 0 24 24" className="size-6" fill="none" stroke="currentColor" strokeWidth="1.5" aria-hidden="true">
          <rect x="3" y="4" width="18" height="16" rx="1.5" />
          <circle cx="9" cy="10" r="1.8" />
          <path d="m4 18 5.5-5 4 3.5L17 13l3 3" />
        </svg>
        <span className="text-sm">{track.name}</span>
      </div>
      <div className="flex flex-1 flex-col p-4 pt-3.5">
        <span className="self-start rounded-full border border-pine-400/60 bg-pine-400/15 px-2.5 py-0.5 text-[0.7rem] font-medium text-pine-700">
          {track.region}
        </span>
        <h3 className="mt-3 font-serif text-[1.4rem] leading-tight text-ink-900">{track.name}</h3>
        <dl className="mt-3 space-y-1 text-sm">
          <Spec label="Duration" value={`${track.duration_days} ${track.duration_days === 1 ? 'day' : 'days'}`} />
          <Spec label="Grade" value={DIFFICULTY_LABEL[track.difficulty]} />
        </dl>
        <Link
          to={`/treks/${track.slug}`}
          className="mt-5 rounded-sm bg-pine-600 after:absolute after:inset-0 py-3 text-center text-sm font-medium text-white hover:bg-pine-700"
        >
          View Trek Details
        </Link>
      </div>
    </li>
  )
}

function Spec({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-baseline gap-2">
      <dt className="text-[0.7rem] tracking-[0.08em] text-ink-400 uppercase">{label}</dt>
      <dd className="text-ink-800">{value}</dd>
    </div>
  )
}

function ArrowButton({
  label,
  disabled,
  onClick,
  children,
}: {
  label: string
  disabled: boolean
  onClick: () => void
  children: ReactNode
}) {
  return (
    <button
      type="button"
      aria-label={label}
      disabled={disabled}
      onClick={onClick}
      className="flex size-11 items-center justify-center rounded-full border border-ink-400/50 text-ink-900 transition hover:bg-ink-900 hover:text-white disabled:pointer-events-none disabled:opacity-35"
    >
      {children}
    </button>
  )
}

function SectionHeading({ title, sub }: { title: string; sub: ReactNode }) {
  return (
    <div>
      <h2 className="font-serif text-3xl tracking-tight text-ink-900 sm:text-[2.3rem]">{title}</h2>
      <p className="mt-2 max-w-xl text-sm leading-relaxed text-ink-700/75">{sub}</p>
    </div>
  )
}

const STEPS = [
  {
    title: 'Pick a trek',
    body: 'Open any route to see every upcoming departure — the guide running it, the date, and the seats left.',
  },
  {
    title: 'Meet your guide',
    body: 'Open their page to see where they live, the treks they have led and their upcoming dates.',
  },
  {
    title: 'Walk in a batch of ten',
    body: 'Small groups, local stay, get to know each other individually.',
  },
]

function HowItWorks() {
  const [active, setActive] = useState(0)
  return (
    <section id="how-it-works" className="scroll-mt-16 bg-paper-100 pt-8 pb-16 sm:pb-24">
      <Container>
        <SectionHeading title="How it works" sub="Three steps between here and the ridge." />
        <ol className="mt-8 grid gap-4 md:grid-cols-3 md:gap-14">
          {STEPS.map((step, i) => (
            <li
              key={step.title}
              onMouseEnter={() => setActive(i)}
              className={`relative rounded-sm border p-5 transition-colors sm:p-6 ${
                active === i ? 'border-pine-400 bg-pine-400/15' : 'border-paper-300 bg-paper-100'
              } ${i > 0 ? 'md:before:absolute md:before:top-1/2 md:before:right-full md:before:mr-2 md:before:w-10 md:before:border-t-2 md:before:border-dashed md:before:border-paper-300 md:before:content-[""]' : ''}`}
            >
              <p className="flex items-center gap-2.5 text-[0.7rem] tracking-[0.12em] text-ink-400 uppercase">
                <span
                  className={`flex size-7 items-center justify-center rounded-full border text-xs tracking-normal ${
                    active === i ? 'border-pine-600 bg-pine-600 text-white' : 'border-ink-400/60 text-ink-700'
                  }`}
                >
                  {i + 1}
                </span>
                Step {i + 1}
              </p>
              <h3 className="mt-3 font-serif text-[1.4rem] text-ink-900">{step.title}</h3>
              <p className={`mt-3 text-sm leading-relaxed ${active === i ? 'text-ink-800' : 'text-ink-700/75'}`}>
                {step.body}
              </p>
            </li>
          ))}
        </ol>
      </Container>
    </section>
  )
}

function Faq() {
  return (
    <section id="faqs" className="scroll-mt-16 border-t border-paper-300 bg-paper-50 py-16 sm:py-20">
      <Container>
        <div className="max-w-[44rem]">
          <SectionHeading
            title="FAQs"
            sub="The five questions we are asked most. Everything else — cancellations and refunds included — is answered on one page."
          />
          <FaqList items={FAQS.slice(0, 5)} />
          <Link to={SITE_LINKS.faqs} className="mt-6 inline-block text-sm font-medium text-pine-600 hover:text-pine-700">
            Explore more queries →
          </Link>
        </div>
      </Container>
    </section>
  )
}

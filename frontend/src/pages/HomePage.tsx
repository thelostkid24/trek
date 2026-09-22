import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState, type CSSProperties, type MouseEvent, type ReactNode } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { listDepartures, trekQueryOptions, type TrackBrief } from '../api/catalog.ts'
import { messageFor } from '../auth/errorMessages.ts'
import { FaqList } from '../components/FaqList.tsx'
import { FAQS } from '../lib/faqs.ts'
import { Reveal } from '../components/Reveal.tsx'
import { revealClass, staggerStyle, useInView } from '../lib/reveal.ts'
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
      {/* Ridgeline art shows until the first photo loads (or if none do). */}
      <Ridgeline className="absolute inset-0 -z-30 size-full" />
      <Mist />
      <HeroSlides />
      <HeroVideo />
      <div
        className="absolute inset-0 -z-10 bg-gradient-to-r from-ink-950/80 via-ink-950/35 to-transparent"
        aria-hidden="true"
      />
      {/* Taller and darker on phones, where the headline sits over the middle of the photo. */}
      <div
        className="absolute inset-x-0 bottom-0 -z-10 h-2/3 bg-gradient-to-t from-ink-950/85 to-transparent sm:h-1/2 sm:from-ink-950/60"
        aria-hidden="true"
      />
      <Container className="w-full pt-24 pb-16 sm:pb-20">
        <Reveal>
          <h1 className="max-w-2xl font-serif text-4xl leading-[1.15] tracking-tight sm:text-[3.4rem]">
            Know your guide before you meet the mountain.
          </h1>
        </Reveal>
        <Reveal delay={150}>
          <a
            href="#treks"
            className="mt-8 inline-block rounded-sm bg-pine-600 px-6 py-3.5 text-sm font-medium text-white transition hover:bg-pine-700 active:scale-[0.98]"
          >
            Browse treks
          </a>
        </Reveal>
      </Container>
    </section>
  )
}

/**
 * Landing photos, in order. Files live in public/; `place` is the small caption.
 * hero-2..4 are free Pexels photos (pexels.com/photo/14229957, 33304433, 10696100).
 */
const HERO_SLIDES: { src: string; place?: string }[] = [
  { src: '/hero.jpg' },
  { src: '/hero-2.jpg', place: 'Rajgad' },
  { src: '/hero-3.jpg', place: 'Western Ghats in the monsoon' },
  { src: '/hero-4.jpg', place: 'Cliffs above Satara' },
]

/** How long each photo holds before the next one fades in. */
const SLIDE_MS = 2000

/**
 * Crossfading hero photos that advance on their own, with a progress bar per photo, like a
 * streaming app's banner. The active bar's CSS animation is the timer: when it ends the next
 * photo comes in, and pausing it (hover, keyboard focus) pauses the rotation. Photos that fail
 * to load drop out, so a missing file just shortens the loop. With reduced motion there's no
 * autoplay or zoom; the bars still switch photos.
 */
function HeroSlides() {
  const [failed, setFailed] = useState<string[]>([])
  const [index, setIndex] = useState(0)
  // The outgoing photo keeps its zoom while it fades, so it doesn't snap back mid-fade.
  const [prev, setPrev] = useState<number | null>(null)
  const [paused, setPaused] = useState(false)
  const slides = HERO_SLIDES.filter((s) => !failed.includes(s.src))
  const active = slides.length > 0 ? index % slides.length : 0
  const show = (i: number) => {
    setPrev(active)
    setIndex(i)
  }

  return (
    <>
      <div className="absolute inset-0 -z-20" aria-hidden="true">
        {slides.map((s, i) => (
          <img
            key={s.src}
            src={s.src}
            alt=""
            fetchPriority={i === 0 ? 'high' : 'low'}
            onError={() => setFailed((f) => [...f, s.src])}
            className={`absolute inset-0 size-full object-cover transition-opacity duration-700 ease-in-out ${
              i === active ? 'opacity-100' : 'opacity-0'
            } ${i === active || i === prev ? 'motion-safe:animate-[hero-zoom_6s_ease-out_both]' : ''}`}
          />
        ))}
      </div>
      {slides.length > 1 && (
        <div
          className="absolute right-5 bottom-3 z-10 flex items-center gap-2 sm:right-10 sm:bottom-5"
          onMouseEnter={() => setPaused(true)}
          onMouseLeave={() => setPaused(false)}
          onFocus={() => setPaused(true)}
          onBlur={() => setPaused(false)}
        >
          {slides[active].place && <span className="mr-2 hidden text-xs text-white/80 sm:inline">{slides[active].place}</span>}
          {slides.map((s, i) => (
            <button
              key={s.src}
              type="button"
              aria-label={`Show photo ${i + 1} of ${slides.length}`}
              aria-current={i === active}
              onClick={() => show(i)}
              className="group/dot py-3"
            >
              {/* The bar stays thin; the button's padding gives it a thumb-sized tap target. */}
              <span
                className={`relative block h-1 overflow-hidden rounded-full bg-white/30 transition-all duration-300 ${
                  i === active ? 'w-10' : 'w-4 group-hover/dot:bg-white/60'
                }`}
              >
                {i === active && (
                  <span
                    key={index}
                    onAnimationEnd={() => show((active + 1) % slides.length)}
                    style={{ animationDuration: `${SLIDE_MS}ms`, animationPlayState: paused ? 'paused' : 'running' }}
                    className="absolute inset-0 origin-left bg-white motion-safe:animate-[hero-progress_linear_both]"
                  />
                )}
              </span>
            </button>
          ))}
        </div>
      )}
    </>
  )
}

/**
 * Muted looping hero clip, dropped at public/hero.webm + public/hero.mp4 (8–15 s, 720p, no audio,
 * a few MB). It fades in once playable; until then — or if the files are missing, the viewer
 * prefers reduced motion, or is saving data — the ridgeline (or hero.jpg) shows instead.
 */
function HeroVideo() {
  const [ready, setReady] = useState(false)
  const [allowed] = useState(
    () =>
      !window.matchMedia('(prefers-reduced-motion: reduce)').matches &&
      !(navigator as Navigator & { connection?: { saveData?: boolean } }).connection?.saveData,
  )
  if (!allowed) return null
  return (
    <video
      autoPlay
      muted
      loop
      playsInline
      preload="auto"
      aria-hidden="true"
      onCanPlay={() => setReady(true)}
      className={`absolute inset-0 -z-20 size-full object-cover transition-opacity duration-1000 ${ready ? 'opacity-100' : 'opacity-0'}`}
    >
      <source src="/hero.webm" type="video/webm" />
      <source src="/hero.mp4" type="video/mp4" />
    </video>
  )
}

/** Two soft banks of valley mist drifting over the ridgeline at different speeds. */
function Mist() {
  const bank = (...spots: string[]) =>
    spots.map((at) => `radial-gradient(ellipse 22% 60% at ${at}, rgb(251 230 214 / 0.16), transparent 70%)`).join(', ')
  return (
    <div className="pointer-events-none absolute inset-0 -z-30 overflow-hidden" aria-hidden="true">
      <div
        className="mist-layer absolute -inset-x-1/4 top-[46%] h-32 blur-xl"
        style={{ backgroundImage: bank('15% 50%', '45% 40%', '80% 55%'), '--mist-duration': '36s' } as CSSProperties}
      />
      <div
        className="mist-layer absolute -inset-x-1/4 top-[62%] h-40 blur-2xl"
        style={{
          backgroundImage: bank('30% 50%', '65% 45%', '95% 60%'),
          '--mist-duration': '52s',
          animationDelay: '-18s',
        } as CSSProperties}
      />
    </div>
  )
}

/** One card per route that has a live departure, in listing order. */
function Routes() {
  const departures = useQuery({ queryKey: ['public-departures'], queryFn: listDepartures })
  const tracks = [...new Map((departures.data?.items ?? []).map((d) => [d.track.slug, d.track])).values()]
  const rail = useRef<HTMLUListElement>(null)
  const { ref: railRef, ...railReveal } = useInView<HTMLDivElement>()
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
          />
          <Link
            to="/treks"
            viewTransition
            className="shrink-0 text-sm font-medium text-pine-600 hover:text-pine-700 sm:ms-auto sm:me-2"
          >
            See all treks →
          </Link>
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
          <div ref={railRef}>
            <ul
              ref={rail}
              onScroll={updateEdges}
              className="-mx-5 mt-8 flex snap-x snap-mandatory scroll-px-5 gap-6 overflow-x-auto px-5 pb-2 [scrollbar-width:none] sm:-mx-10 sm:scroll-px-10 sm:px-10 [&::-webkit-scrollbar]:hidden"
            >
              {tracks.map((t, i) => (
                <RouteCard key={t.slug} track={t} reveal={railReveal} delay={Math.min(i, 5) * 80} />
              ))}
            </ul>
          </div>
        )}
      </Container>
    </section>
  )
}

/**
 * The cover shares a view-transition name with the trek page hero, so opening a card grows
 * its cover into the hero. The trek is fetched before navigating (usually already warm from
 * hover) so the hero exists when the transition snapshots the new page.
 */
function RouteCard({
  track,
  reveal,
  delay,
}: {
  track: TrackBrief
  reveal: { inView: boolean; settled: boolean }
  delay: number
}) {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const to = `/treks/${track.slug}`
  const prefetch = () => void queryClient.prefetchQuery(trekQueryOptions(track.slug))
  const open = async (e: MouseEvent<HTMLAnchorElement>) => {
    if (e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return
    e.preventDefault()
    await queryClient.ensureQueryData(trekQueryOptions(track.slug)).catch(() => undefined)
    void navigate(to, { viewTransition: true })
  }

  return (
    <li
      style={staggerStyle(reveal, delay)}
      className={`group relative flex w-[78vw] max-w-[18.75rem] shrink-0 snap-start flex-col overflow-hidden rounded-sm border border-paper-300 bg-paper-50 sm:w-[18.75rem] ${revealClass(reveal.inView)} hover:-translate-y-1 hover:shadow-lg hover:shadow-ink-900/10`}
    >
      {/* Route photos aren't in the catalog yet; the ridgeline stands in. */}
      <div
        className="relative aspect-[4/3] overflow-hidden bg-brand-950"
        style={{ viewTransitionName: `trek-cover-${track.slug}`, viewTransitionClass: 'trek-cover' }}
      >
        <Ridgeline className="absolute inset-0 size-full transition-transform duration-700 ease-out group-hover:scale-105" />
      </div>
      <div className="flex flex-1 flex-col p-4 pt-3.5">
        <div className="flex flex-wrap gap-1.5">
          {[track.region, `${track.duration_days} ${track.duration_days === 1 ? 'day' : 'days'}`].map((tag) => (
            <span
              key={tag}
              className="rounded-full border border-pine-400/60 bg-pine-400/15 px-2.5 py-0.5 text-[0.7rem] font-medium text-pine-700"
            >
              {tag}
            </span>
          ))}
        </div>
        <h3 className="mt-3 font-serif text-[1.4rem] leading-tight text-ink-900">{track.name}</h3>
        <Link
          to={to}
          viewTransition
          onPointerEnter={prefetch}
          onFocus={prefetch}
          onTouchStart={prefetch}
          onClick={(e) => void open(e)}
          className="mt-5 rounded-sm bg-pine-600 after:absolute after:inset-0 py-3 text-center text-sm font-medium text-white transition hover:bg-pine-700 active:scale-[0.98]"
        >
          View Trek Details
        </Link>
      </div>
    </li>
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

function SectionHeading({ title, sub }: { title: string; sub?: ReactNode }) {
  return (
    <div>
      <h2 className="font-serif text-3xl tracking-tight text-ink-900 sm:text-[2.3rem]">{title}</h2>
      {sub && <p className="mt-2 max-w-xl text-sm leading-relaxed text-ink-700/75">{sub}</p>}
    </div>
  )
}

const STEPS = [
  {
    title: 'Pick a trek',
    body: 'Open any route to see every upcoming departure — the guide running it, the date, etc.',
  },
  {
    title: 'Choose your guide',
    body: 'Pick from the list of guides and select your favourite. You can see where they live, the treks they have led and their upcoming dates.',
  },
  {
    title: 'Book your departure',
    body: 'Enjoy the journey with a small group of people, and get to know the local culture along the way.',
  },
]

function HowItWorks() {
  const [active, setActive] = useState(0)
  const { ref: stepsRef, ...steps } = useInView<HTMLOListElement>()
  return (
    <section id="how-it-works" className="scroll-mt-16 bg-paper-100 pt-8 pb-16 sm:pb-24">
      <Container>
        <SectionHeading title="How it works" sub="Three steps between here and the ridge." />
        <ol ref={stepsRef} className="mt-8 grid gap-4 md:grid-cols-3 md:gap-14">
          {STEPS.map((step, i) => (
            <li
              key={step.title}
              onMouseEnter={() => setActive(i)}
              style={staggerStyle(steps, i * 120)}
              className={`relative rounded-sm border p-5 sm:p-6 ${revealClass(steps.inView)} transition-colors ${
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
        <div className="mx-auto max-w-[44rem]">
          <SectionHeading
            title="FAQs"
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

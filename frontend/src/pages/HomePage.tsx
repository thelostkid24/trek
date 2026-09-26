import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState, type CSSProperties, type FormEvent, type MouseEvent, type ReactNode } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { listCatalog, MAX_GROUP_SIZE, trekQueryOptions, type CatalogTrek, type GuideBrief } from '../api/catalog.ts'
import { messageFor } from '../auth/errorMessages.ts'
import { FaqList } from '../components/FaqList.tsx'
import { FAQS } from '../lib/faqs.ts'
import { monthKey, monthLabel, rupees, shortRange } from '../lib/format.ts'
import { revealClass, staggerStyle, useInView } from '../lib/reveal.ts'
import { Ridgeline } from '../components/Ridgeline.tsx'
import { SITE_LINKS } from '../lib/siteLinks.ts'

/** Same query (and cache) as /treks, so opening "See all treks" from here is instant. */
const useCatalog = () => useQuery({ queryKey: ['public-catalog'], queryFn: listCatalog })

export function HomePage() {
  return (
    <div className="bg-paper-50 font-grotesk text-ink-900">
      <Hero />
      <Promises />
      <Statement />
      <SmallGroups />
      <Destinations />
      <Guides />
      <HowItWorks />
      <Faq />
      <ClosingCall />
    </div>
  )
}

function Container({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <div className={`mx-auto max-w-[90rem] px-5 sm:px-10 ${className}`}>{children}</div>
}

function Arrow({ className = '' }: { className?: string }) {
  return (
    <svg viewBox="0 0 20 20" className={className} fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true">
      <path d="M5 10h10M11 6l4 4-4 4" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

/** A pill with a round arrow at its end; the arrow turns toward the destination on hover. */
function PillLink({ to, dark = false, children }: { to: string; dark?: boolean; children: ReactNode }) {
  const tone = dark ? 'bg-ink-950 text-white hover:bg-ink-800' : 'bg-white text-ink-950 hover:bg-paper-100'
  const dot = dark ? 'bg-white text-ink-950' : 'bg-ink-950 text-white'
  const className = `group inline-flex items-center gap-4 rounded-full py-1.5 pr-1.5 pl-6 text-sm font-medium transition active:scale-[0.98] ${tone}`
  const inner = (
    <>
      {children}
      <span className={`flex size-10 items-center justify-center rounded-full transition-transform duration-300 group-hover:-rotate-45 ${dot}`}>
        <Arrow className="size-4" />
      </span>
    </>
  )
  return to.includes('#') ? (
    <a href={to} className={className}>
      {inner}
    </a>
  ) : (
    <Link to={to} viewTransition className={className}>
      {inner}
    </Link>
  )
}

function Eyebrow({ children, light = false }: { children: ReactNode; light?: boolean }) {
  return (
    <p className={`flex items-center gap-2 text-xs font-medium tracking-[0.16em] uppercase ${light ? 'text-white/70' : 'text-pine-600'}`}>
      <span className={`h-px w-6 ${light ? 'bg-white/50' : 'bg-pine-400'}`} aria-hidden="true" />
      {children}
    </p>
  )
}

/** Each word rises out of its own clipped line, one after another (see `.word-rise`). */
function RisingWords({ text, from = 0, className = '' }: { text: string; from?: number; className?: string }) {
  return text.split(' ').map((word, i) => (
    <span key={i}>
      <span className="-mb-[0.14em] inline-block overflow-hidden pb-[0.14em] align-top">
        <span className={`word-rise ${className}`} style={{ '--w': from + i } as CSSProperties}>
          {word}
        </span>
      </span>{' '}
    </span>
  ))
}

function Hero() {
  const catalog = useCatalog()
  return (
    <section>
      <div className="relative isolate overflow-hidden bg-ink-900 text-white">
        {/* Ridgeline art shows until the first photo loads (or if none do). */}
        <Ridgeline className="absolute inset-0 -z-30 size-full" />
        <Mist />
        <HeroSlides />
        <HeroVideo />
        <div className="absolute inset-0 -z-10 bg-gradient-to-r from-ink-950/75 via-ink-950/30 to-transparent" aria-hidden="true" />
        <div className="absolute inset-x-0 bottom-0 -z-10 h-2/3 bg-gradient-to-t from-ink-950/80 to-transparent" aria-hidden="true" />

        <Container className="grid min-h-[44rem] items-end gap-10 pt-28 pb-16 lg:min-h-svh lg:grid-cols-[1fr_23rem] lg:pb-20">
          <div>
            <p className="fade-rise inline-flex items-center gap-2 rounded-full border border-white/25 bg-white/10 px-3.5 py-1.5 text-xs backdrop-blur-sm">
              <span className="size-1.5 animate-pulse rounded-full bg-laterite-400" aria-hidden="true" />
              Batches of {MAX_GROUP_SIZE} · every paid date runs
            </p>
            <h1 className="mt-6 max-w-3xl text-[2.6rem] leading-[1.05] font-light tracking-[-0.03em] sm:text-6xl lg:text-[4.6rem]">
              <RisingWords text="Know your guide" />
              <RisingWords text="before you meet the mountain." from={3} className="text-white/60" />
            </h1>
            <p className="fade-rise mt-6 max-w-md text-sm leading-relaxed text-white/75 sm:text-base" style={{ '--d': '700ms' } as CSSProperties}>
              Small-batch treks led by local guides you choose by name, with their credentials and reviews in front of you before you pay.
            </p>
            <div className="fade-rise mt-8 flex flex-wrap items-center gap-5" style={{ '--d': '850ms' } as CSSProperties}>
              <PillLink to="/treks">Browse treks</PillLink>
              <a href="#how-it-works" className="text-sm text-white/80 underline-offset-4 hover:text-white hover:underline">
                How it works
              </a>
            </div>
          </div>
          <Finder treks={catalog.data?.items ?? []} />
        </Container>
      </div>
    </section>
  )
}

const LENGTHS = [
  { key: 'day', label: 'One day' },
  { key: 'weekend', label: 'Weekend' },
  { key: 'long', label: '3 days +' },
]

/**
 * The floating search card. "By trek" opens that trek's page; "By month" opens /treks grouped by
 * date with the same `month` / `length` filters the catalog page reads from its URL.
 */
function Finder({ treks }: { treks: CatalogTrek[] }) {
  const navigate = useNavigate()
  const [mode, setMode] = useState<'trek' | 'month'>('trek')
  const [slug, setSlug] = useState('')
  const [month, setMonth] = useState('')
  const [length, setLength] = useState('')
  const months = useMemo(
    () => [...new Set(treks.flatMap((t) => t.departures.map((d) => monthKey(d.start_date))))].sort(),
    [treks],
  )
  const openDates = treks.reduce((n, t) => n + t.departures.filter((d) => d.bookable).length, 0)

  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (mode === 'trek') {
      void navigate(slug ? `/treks/${slug}` : '/treks', { viewTransition: true })
      return
    }
    const params = new URLSearchParams({ view: 'dates' })
    if (month) params.set('month', month)
    if (length) params.set('length', length)
    void navigate(`/treks?${params}`, { viewTransition: true })
  }

  return (
    <form
      onSubmit={submit}
      aria-label="Find a departure"
      className="fade-rise w-full rounded-[1.5rem] bg-white p-2 text-ink-900 shadow-2xl shadow-ink-950/30 sm:max-w-sm lg:justify-self-end"
      style={{ '--d': '600ms' } as CSSProperties}
    >
      <div className="relative grid grid-cols-2 rounded-full bg-paper-100 p-1 text-sm" role="tablist" aria-label="Search by">
        {/* The white thumb slides under whichever tab is picked. */}
        <span
          aria-hidden="true"
          className={`absolute inset-y-1 left-1 w-[calc(50%-0.25rem)] rounded-full bg-white shadow-sm transition-transform duration-300 ease-out ${
            mode === 'month' ? 'translate-x-full' : ''
          }`}
        />
        {(['trek', 'month'] as const).map((m) => (
          <button
            key={m}
            type="button"
            role="tab"
            aria-selected={mode === m}
            onClick={() => setMode(m)}
            className={`relative py-2 font-medium transition-colors ${mode === m ? 'text-ink-950' : 'text-ink-400 hover:text-ink-700'}`}
          >
            {m === 'trek' ? 'By trek' : 'By month'}
          </button>
        ))}
      </div>

      <div className="space-y-3 p-3 pt-4">
        {mode === 'trek' ? (
          <FinderField label="Trek" icon={<PinIcon />}>
            <select value={slug} onChange={(e) => setSlug(e.target.value)} className={SELECT}>
              <option value="">Any trek</option>
              {treks.map((t) => (
                <option key={t.slug} value={t.slug}>
                  {t.name} · {t.region}
                </option>
              ))}
            </select>
          </FinderField>
        ) : (
          <div className="grid grid-cols-2 gap-3">
            <FinderField label="Month" icon={<CalendarIcon />}>
              <select value={month} onChange={(e) => setMonth(e.target.value)} className={SELECT}>
                <option value="">Any</option>
                {months.map((key) => (
                  <option key={key} value={key}>
                    {monthLabel(key)}
                  </option>
                ))}
              </select>
            </FinderField>
            <FinderField label="Length" icon={<ClockIcon />}>
              <select value={length} onChange={(e) => setLength(e.target.value)} className={SELECT}>
                <option value="">Any</option>
                {LENGTHS.map((l) => (
                  <option key={l.key} value={l.key}>
                    {l.label}
                  </option>
                ))}
              </select>
            </FinderField>
          </div>
        )}
        <FinderField label="Group" icon={<PeopleIcon />}>
          <p className="py-2.5 pr-3 pl-10 text-sm text-ink-700">
            Never more than {MAX_GROUP_SIZE}, plus your guide
          </p>
        </FinderField>
        <button
          type="submit"
          className="group flex w-full items-center justify-center gap-2 rounded-full bg-ink-950 py-3.5 text-sm font-medium text-white transition hover:bg-pine-700 active:scale-[0.98]"
        >
          {mode === 'trek' && slug ? 'See this trek' : 'Find departures'}
          <Arrow className="size-4 transition-transform group-hover:translate-x-0.5" />
        </button>
        {openDates > 0 && (
          <p className="text-center text-xs text-ink-400">
            {openDates} open {openDates === 1 ? 'date' : 'dates'} this season
          </p>
        )}
      </div>
    </form>
  )
}

const SELECT =
  'w-full appearance-none rounded-full bg-transparent py-2.5 pr-8 pl-10 text-sm text-ink-900 outline-none focus-visible:ring-2 focus-visible:ring-pine-400'

function FinderField({ label, icon, children }: { label: string; icon: ReactNode; children: ReactNode }) {
  return (
    <label className="block">
      <span className="mb-1 block px-1 text-xs font-medium text-ink-700">{label}</span>
      <span className="relative block rounded-full border border-paper-300 bg-paper-50 transition-colors focus-within:border-pine-400 hover:border-ink-400/50">
        <span className="pointer-events-none absolute top-1/2 left-3.5 -translate-y-1/2 text-ink-400">{icon}</span>
        {children}
      </span>
    </label>
  )
}

const ICON = 'size-4'
const PinIcon = () => (
  <svg viewBox="0 0 20 20" className={ICON} fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden="true">
    <path d="M10 18s6-5.2 6-10a6 6 0 1 0-12 0c0 4.8 6 10 6 10Z" />
    <circle cx="10" cy="8" r="2.2" />
  </svg>
)
const CalendarIcon = () => (
  <svg viewBox="0 0 20 20" className={ICON} fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden="true">
    <rect x="3" y="4.5" width="14" height="12.5" rx="2" />
    <path d="M3 8.5h14M7 2.5v4M13 2.5v4" strokeLinecap="round" />
  </svg>
)
const ClockIcon = () => (
  <svg viewBox="0 0 20 20" className={ICON} fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden="true">
    <circle cx="10" cy="10" r="7" />
    <path d="M10 6v4l2.5 2" strokeLinecap="round" />
  </svg>
)
const PeopleIcon = () => (
  <svg viewBox="0 0 20 20" className={ICON} fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden="true">
    <circle cx="7.5" cy="7" r="2.8" />
    <path d="M2.5 16.5c.6-2.8 2.6-4.3 5-4.3s4.4 1.5 5 4.3M13 4.6a2.7 2.7 0 0 1 0 5M14.8 12.4c1.5.5 2.5 1.8 2.8 4.1" strokeLinecap="round" />
  </svg>
)

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
const SLIDE_MS = 6000

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
            } ${i === active || i === prev ? 'motion-safe:animate-[hero-zoom_9s_ease-out_both]' : ''}`}
          />
        ))}
      </div>
      {slides.length > 1 && (
        <div
          className="absolute bottom-3 left-5 z-10 flex items-center gap-2 sm:bottom-4 sm:left-10"
          onMouseEnter={() => setPaused(true)}
          onMouseLeave={() => setPaused(false)}
          onFocus={() => setPaused(true)}
          onBlur={() => setPaused(false)}
        >
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
          {slides[active].place && (
            <span key={slides[active].src} className="fade-rise ml-2 hidden text-xs text-white/80 sm:inline">
              {slides[active].place}
            </span>
          )}
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

/** Things that are true of every departure, drifting past in a slow ribbon. */
const PROMISES = [
  `Groups of ${MAX_GROUP_SIZE}, never more`,
  'Pick your guide by name',
  'Every paid date runs',
  'Weekly snow reports from the trail',
  'Tent counts at the busiest camp',
  'No booking fee',
]

function Promises() {
  const row = (hidden: boolean) => (
    <ul className="flex shrink-0 items-center" aria-hidden={hidden || undefined}>
      {PROMISES.map((p) => (
        <li key={p} className="flex items-center gap-8 pr-8 text-xl font-light whitespace-nowrap text-ink-700 sm:text-2xl">
          {p}
          <svg viewBox="0 0 24 24" className="size-5 text-laterite-500" fill="currentColor" aria-hidden="true">
            <path d="M2 19 8.5 8h3L14 12l2.5-5L22 19Z" />
          </svg>
        </li>
      ))}
    </ul>
  )
  return (
    <section aria-label="Our promises" className="marquee overflow-hidden border-b border-paper-300 py-6">
      <div className="marquee-track flex w-max">
        {row(false)}
        {row(true)}
      </div>
    </section>
  )
}

/** One sentence with photos set into it; the capsules open and the words fill in as it scrolls past. */
function Statement() {
  const { ref, inView } = useInView<HTMLParagraphElement>()
  const words = (text: string) =>
    text.split(' ').map((w, i) => (
      <span key={i} className="scroll-fill">
        {w}{' '}
      </span>
    ))
  const capsule = (src: string, delay: number) => (
    <span
      aria-hidden="true"
      className="capsule mx-[0.1em] inline-block h-[0.8em] rounded-full bg-cover bg-center align-[-0.08em]"
      style={{ backgroundImage: `url(${src})`, width: inView ? '1.9em' : '0.35em', '--d': `${delay}ms` } as CSSProperties}
    />
  )
  return (
    <section className="py-20 sm:py-32">
      <Container>
        <p
          ref={ref}
          className="mx-auto max-w-5xl text-center text-[2rem] leading-[1.3] font-light tracking-[-0.02em] text-ink-900 sm:text-5xl lg:text-[3.5rem]"
        >
          {words('Walk with')} {capsule('/hero-2.jpg', 0)} {words('nine others, behind a guide')} {capsule('/hero-3.jpg', 150)}{' '}
          {words('you picked by name, into valleys')} {capsule('/hero-4.jpg', 300)} {words('that still feel empty.')}
        </p>
        <p className="mx-auto mt-8 max-w-md text-center text-sm leading-relaxed text-ink-700/80">
          You see who is leading before you pay, and your date runs even if you are the only one booked.
        </p>
      </Container>
    </section>
  )
}

/** Counts from zero to `to` once `start` turns true. Reduced motion shows the number straight away. */
function useCountUp(to: number, start: boolean, ms = 1400) {
  const [value, setValue] = useState(0)
  const [still] = useState(() => window.matchMedia('(prefers-reduced-motion: reduce)').matches)
  useEffect(() => {
    if (!start || still) return
    let frame = 0
    const t0 = performance.now()
    const tick = (now: number) => {
      const p = Math.min(1, (now - t0) / ms)
      setValue(Math.round(to * (1 - Math.pow(1 - p, 3))))
      if (p < 1) frame = requestAnimationFrame(tick)
    }
    frame = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frame)
  }, [to, start, ms, still])
  return start && still ? to : value
}

function Stat({ to, suffix = '', label, start }: { to: number; suffix?: string; label: string; start: boolean }) {
  const value = useCountUp(to, start)
  return (
    <div className="flex items-baseline justify-between gap-6 border-t border-paper-300 py-5">
      <dt className="order-2 max-w-[12rem] text-right text-sm text-ink-700/80">{label}</dt>
      <dd className="text-5xl font-light tracking-tight text-ink-950 tabular-nums sm:text-6xl">
        {value}
        {suffix}
      </dd>
    </div>
  )
}

/** A photo beside the numbers that make a batch small: the cap, the minimum, and who leads. */
function SmallGroups() {
  const catalog = useCatalog()
  const guides = guideRoster(catalog.data?.items ?? []).length
  const { ref, inView } = useInView<HTMLDivElement>()
  return (
    <section className="pb-20 sm:pb-32">
      <Container className="grid items-center gap-12 lg:grid-cols-2 lg:gap-20">
        <div className="relative aspect-[4/5] overflow-hidden rounded-[1.75rem] sm:aspect-[5/4] lg:aspect-[4/5]">
          <img src="/hero-3.jpg" alt="The Western Ghats in the monsoon" className="parallax absolute inset-0 size-full object-cover" />
          <SeatCard inView={inView} />
        </div>
        <div ref={ref} className={revealClass(inView)}>
          <Eyebrow>Why small</Eyebrow>
          <h2 className="mt-5 text-4xl leading-[1.1] font-light tracking-[-0.02em] sm:text-5xl">
            A group small enough that your guide knows everyone’s pace by lunch.
          </h2>
          <p className="mt-5 max-w-lg text-sm leading-relaxed text-ink-700/80 sm:text-base">
            One guide can genuinely watch ten people. Above that the group stretches out, the slowest walker sets a pace nobody enjoys, and altitude symptoms get missed.
          </p>
          <dl className="mt-10 border-b border-paper-300">
            <Stat to={MAX_GROUP_SIZE} label="trekkers at most on any departure" start={inView} />
            <Stat to={1} label="paid booking is all it takes for a date to run" start={inView} />
            {guides > 0 && <Stat to={guides} label={`local ${guides === 1 ? 'guide' : 'guides'} leading dates this season`} start={inView} />}
          </dl>
        </div>
      </Container>
    </section>
  )
}

/** Glass card over the photo: ten seats that fill one by one, the tenth left open for you. */
function SeatCard({ inView }: { inView: boolean }) {
  return (
    <div className="absolute bottom-4 left-4 rounded-2xl border border-white/20 bg-ink-950/40 p-4 text-white backdrop-blur-md sm:bottom-6 sm:left-6">
      <p className="text-xs text-white/70">Seats on a batch</p>
      <div className="mt-3 flex gap-1.5" aria-hidden="true">
        {Array.from({ length: MAX_GROUP_SIZE }, (_, i) => (
          <span
            key={i}
            className={`size-3 rounded-full transition-all duration-500 ${
              inView ? (i < MAX_GROUP_SIZE - 1 ? 'scale-100 bg-white' : 'scale-100 border border-dashed border-white bg-transparent') : 'scale-0 bg-white'
            }`}
            style={{ transitionDelay: `${300 + i * 90}ms` }}
          />
        ))}
      </div>
      <p className="mt-3 text-sm">…and one for you.</p>
    </div>
  )
}

/**
 * Up to six treks in a mosaic: the first large, the rest filling in around it. A cover shares its
 * view-transition name with the trek page hero, so opening a card grows it into the hero. The trek is
 * fetched before navigating (usually already warm from hover) so the hero exists when the new page is snapshotted.
 */
function Destinations() {
  const catalog = useCatalog()
  const treks = (catalog.data?.items ?? []).slice(0, 6)
  const { ref, ...reveal } = useInView<HTMLUListElement>()

  return (
    <section id="treks" className="scroll-mt-16 border-t border-paper-300 py-20 sm:py-28">
      <Container>
        <div className="flex flex-wrap items-end justify-between gap-6">
          <div>
            <Eyebrow>This season</Eyebrow>
            <h2 className="mt-4 text-4xl font-light tracking-[-0.02em] sm:text-5xl">Where we’re walking</h2>
          </div>
          <PillLink to="/treks" dark>
            See all treks
          </PillLink>
        </div>

        {catalog.isPending ? (
          <ul className="mt-10 grid gap-3 sm:grid-cols-2 lg:auto-rows-[15rem] lg:grid-cols-4" aria-busy="true" aria-label="Loading treks">
            {[0, 1, 2, 3].map((i) => (
              <li key={i} className={`h-72 animate-pulse rounded-[1.5rem] bg-paper-200 lg:h-auto ${i === 0 ? 'lg:col-span-2 lg:row-span-2' : i === 3 ? 'lg:col-span-2' : ''}`} />
            ))}
          </ul>
        ) : catalog.isError ? (
          <div className="mt-10 rounded-[1.5rem] border border-paper-300 bg-paper-50 p-8 text-center">
            <p className="text-ink-700">{messageFor(catalog.error)}</p>
            <button
              type="button"
              onClick={() => void catalog.refetch()}
              className="mt-4 rounded-full bg-ink-950 px-5 py-2.5 text-sm font-medium text-white hover:bg-pine-700"
            >
              Try again
            </button>
          </div>
        ) : treks.length === 0 ? (
          <div className="mt-10 rounded-[1.5rem] border border-paper-300 bg-paper-50 p-10 text-center">
            <p className="text-2xl font-light">New departures are on the way</p>
            <p className="mt-2 text-sm text-ink-700/75">Our guides are planning the next batches. Check back soon.</p>
          </div>
        ) : (
          <ul
            ref={ref}
            className={`mt-10 grid gap-3 sm:grid-cols-2 ${
              treks.length >= 4 ? 'lg:auto-rows-[15rem] lg:grid-cols-4' : `lg:auto-rows-[26rem] ${treks.length === 3 ? 'lg:grid-cols-3' : ''}`
            }`}
          >
            {treks.map((t, i) => (
              <DestinationCard key={t.slug} trek={t} span={tileSpan(i, treks.length)} large={i === 0 && treks.length >= 4} reveal={reveal} delay={i * 90} />
            ))}
          </ul>
        )}
      </Container>
    </section>
  )
}

/** Mosaic spans on a 4-column grid: 2×2 lead, two singles, then wide tiles. Three or fewer sit side by side. */
function tileSpan(i: number, n: number) {
  if (n < 4) return ''
  if (i === 0) return 'sm:col-span-2 lg:row-span-2'
  if (i === 4 && n === 5) return 'sm:col-span-2 lg:col-span-4'
  return i >= 3 ? 'lg:col-span-2' : ''
}

function DestinationCard({
  trek,
  span,
  large,
  reveal,
  delay,
}: {
  trek: CatalogTrek
  span: string
  large: boolean
  reveal: { inView: boolean; settled: boolean }
  delay: number
}) {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const to = `/treks/${trek.slug}`
  const prefetch = () => void queryClient.prefetchQuery(trekQueryOptions(trek.slug))
  const open = async (e: MouseEvent<HTMLAnchorElement>) => {
    if (e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return
    e.preventDefault()
    await queryClient.ensureQueryData(trekQueryOptions(trek.slug)).catch(() => undefined)
    void navigate(to, { viewTransition: true })
  }
  const from = trek.departures.length > 0 ? Math.min(...trek.departures.map((d) => d.price_paise)) : null
  const next = trek.departures[0]

  return (
    <li style={staggerStyle(reveal, delay)} className={`${revealClass(reveal.inView)} ${span}`}>
      <Link
        to={to}
        viewTransition
        onPointerEnter={prefetch}
        onFocus={prefetch}
        onTouchStart={prefetch}
        onClick={(e) => void open(e)}
        className="group relative isolate flex h-80 flex-col justify-between overflow-hidden rounded-[1.5rem] bg-brand-950 p-5 text-white sm:h-96 lg:h-full"
      >
        <div
          className="absolute inset-0 -z-10"
          style={{ viewTransitionName: `trek-cover-${trek.slug}`, viewTransitionClass: 'trek-cover' }}
        >
          {trek.cover_url ? (
            <img src={trek.cover_url} alt="" className="size-full object-cover transition-transform duration-[1200ms] ease-out group-hover:scale-110" />
          ) : (
            <Ridgeline className="size-full transition-transform duration-[1200ms] ease-out group-hover:scale-110" />
          )}
        </div>
        <div className="absolute inset-0 -z-10 bg-gradient-to-t from-ink-950/80 via-ink-950/10 to-ink-950/20" aria-hidden="true" />

        <div className="flex items-start justify-between gap-3">
          <div className="flex flex-wrap gap-1.5">
            {[trek.region, `${trek.duration_days} ${trek.duration_days === 1 ? 'day' : 'days'}`].map((tag) => (
              <span key={tag} className="rounded-full border border-white/25 bg-white/15 px-2.5 py-1 text-[0.7rem] font-medium backdrop-blur-sm">
                {tag}
              </span>
            ))}
          </div>
          <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-white text-ink-950 transition duration-300 group-hover:-rotate-45 group-hover:bg-laterite-400">
            <Arrow className="size-4" />
          </span>
        </div>

        <div className="transition-transform duration-500 group-hover:-translate-y-1">
          <h3 className={`leading-tight font-normal tracking-[-0.01em] ${large ? 'text-3xl sm:text-4xl' : 'text-2xl'}`}>{trek.name}</h3>
          {large && <p className="mt-2 line-clamp-2 max-w-md text-sm text-white/75">{trek.summary}</p>}
          <p className="mt-2 text-xs text-white/75">
            {next ? `Next ${shortRange(next.start_date, next.end_date)}` : trek.season_label ?? 'Dates coming soon'}
            {from !== null && ` · from ${rupees(from)}`}
          </p>
        </div>
      </Link>
    </li>
  )
}

type RosterEntry = { guide: GuideBrief; dates: number; treks: string[] }

/** Everyone leading a listed date, busiest first. Derived at read time from the catalog. */
function guideRoster(treks: CatalogTrek[]): RosterEntry[] {
  const byId = new Map<string, RosterEntry>()
  for (const t of treks) {
    for (const d of t.departures) {
      const entry = byId.get(d.guide.id) ?? { guide: d.guide, dates: 0, treks: [] }
      entry.dates += 1
      if (!entry.treks.includes(t.name)) entry.treks.push(t.name)
      byId.set(d.guide.id, entry)
    }
  }
  return [...byId.values()].sort((a, b) => b.dates - a.dates)
}

function Guides() {
  const catalog = useCatalog()
  const roster = guideRoster(catalog.data?.items ?? []).slice(0, 8)
  const { ref, ...reveal } = useInView<HTMLUListElement>()
  if (roster.length === 0) return null

  return (
    <section className="pb-20 sm:pb-28">
      <Container>
        <div className="grid gap-6 border-t border-paper-300 pt-16 lg:grid-cols-[1fr_1.2fr] lg:items-end">
          <div>
            <Eyebrow>The people who lead</Eyebrow>
            <h2 className="mt-4 text-4xl font-light tracking-[-0.02em] sm:text-5xl">Meet your guide first</h2>
          </div>
          <p className="max-w-lg text-sm leading-relaxed text-ink-700/80 lg:justify-self-end">
            Every date is listed against the guide running it. Read where they live, how long they have led, and what past trekkers said, then pick.
          </p>
        </div>
        <ul
          ref={ref}
          className="-mx-5 mt-10 flex snap-x snap-mandatory scroll-px-5 gap-4 overflow-x-auto px-5 pb-2 [scrollbar-width:none] sm:-mx-10 sm:scroll-px-10 sm:px-10 [&::-webkit-scrollbar]:hidden"
        >
          {roster.map((entry, i) => (
            <GuideTile key={entry.guide.id} entry={entry} reveal={reveal} delay={i * 80} />
          ))}
        </ul>
      </Container>
    </section>
  )
}

function GuideTile({ entry, reveal, delay }: { entry: RosterEntry; reveal: { inView: boolean; settled: boolean }; delay: number }) {
  const { guide, dates, treks } = entry
  const name = guide.full_name ?? 'Guide'
  const initials = name
    .split(/\s+/)
    .filter(Boolean)
    .map((p) => p[0])
    .slice(0, 2)
    .join('')
    .toUpperCase()

  return (
    <li style={staggerStyle(reveal, delay)} className={`w-60 shrink-0 snap-start sm:w-64 ${revealClass(reveal.inView)}`}>
      <Link to={`/guides/${guide.id}`} viewTransition className="group block">
        <div className="relative aspect-[4/5] overflow-hidden rounded-[1.5rem] bg-gradient-to-br from-pine-600 to-brand-950">
          {guide.avatar_url ? (
            <img
              src={guide.avatar_url}
              alt={`${name}'s photo`}
              className="size-full object-cover grayscale-[35%] transition duration-700 group-hover:scale-105 group-hover:grayscale-0"
            />
          ) : (
            <span className="flex size-full items-center justify-center text-7xl font-light text-white/85" aria-hidden="true">
              {initials}
            </span>
          )}
          <span className="absolute inset-x-3 bottom-3 flex translate-y-3 items-center justify-between rounded-full bg-white/90 py-1.5 pr-1.5 pl-4 text-xs font-medium text-ink-950 opacity-0 backdrop-blur transition duration-300 group-hover:translate-y-0 group-hover:opacity-100 group-focus-visible:translate-y-0 group-focus-visible:opacity-100">
            Read their page
            <span className="flex size-7 items-center justify-center rounded-full bg-ink-950 text-white">
              <Arrow className="size-3.5" />
            </span>
          </span>
        </div>
        <p className="mt-4 text-lg leading-tight">{name}</p>
        <p className="mt-1 line-clamp-1 text-xs text-ink-700/75">
          {dates} upcoming {dates === 1 ? 'date' : 'dates'} · {treks.join(', ')}
        </p>
      </Link>
    </li>
  )
}

const STEPS = [
  {
    title: 'Pick a trek',
    body: 'Open any route to see every upcoming departure — the guide running it, the date, the seats left.',
    icon: (
      <path d="M3 17 8 7l3 5 2-3 4 8H3Z M14.5 5.5a1.5 1.5 0 1 0 0-.01" strokeLinejoin="round" />
    ),
  },
  {
    title: 'Choose your guide',
    body: 'See where they live, the treks they have led, their credentials and what past trekkers said.',
    icon: (
      <>
        <circle cx="10" cy="7" r="3" />
        <path d="M4 17c.8-3.2 3.1-5 6-5s5.2 1.8 6 5" strokeLinecap="round" />
      </>
    ),
  },
  {
    title: 'Book your departure',
    body: 'Hold your seats and pay in one go. Walk with a small group and get to know the local culture along the way.',
    icon: <path d="M2.5 16.5 10 4l7.5 12.5M7 16.5 10 11l3 5.5M1.5 16.5h17" strokeLinecap="round" strokeLinejoin="round" />,
  },
]

function HowItWorks() {
  const { ref, ...steps } = useInView<HTMLOListElement>()
  return (
    <section id="how-it-works" className="scroll-mt-16 border-t border-paper-300 py-20 sm:py-28">
      <Container>
        <div className="mx-auto max-w-2xl text-center">
          <div className="flex justify-center">
            <Eyebrow>How it works</Eyebrow>
          </div>
          <h2 className="mt-4 text-4xl font-light tracking-[-0.02em] sm:text-5xl">Three steps between here and the ridge</h2>
        </div>
        <ol ref={ref} className="relative mt-14 grid gap-10 md:grid-cols-3 md:gap-8">
          {/* The trail between the steps draws itself left to right. */}
          <span
            aria-hidden="true"
            className={`trail-draw absolute top-8 right-[16.6%] left-[16.6%] hidden origin-left border-t-2 border-dashed border-pine-400/60 transition-transform duration-[1600ms] ease-out md:block ${
              steps.inView ? 'scale-x-100' : 'scale-x-0'
            }`}
          />
          {STEPS.map((step, i) => (
            <li key={step.title} style={staggerStyle(steps, 200 + i * 250)} className={`group relative text-center ${revealClass(steps.inView)}`}>
              <span className="relative mx-auto flex size-16 items-center justify-center rounded-full border border-paper-300 bg-white text-pine-600 shadow-sm transition duration-300 group-hover:-translate-y-1 group-hover:bg-pine-600 group-hover:text-white">
                <svg viewBox="0 0 20 20" className="size-6" fill="none" stroke="currentColor" strokeWidth="1.5" aria-hidden="true">
                  {step.icon}
                </svg>
                <span className="absolute -top-1 -right-1 flex size-6 items-center justify-center rounded-full bg-ink-950 text-[0.7rem] text-white">
                  {i + 1}
                </span>
              </span>
              <h3 className="mt-6 text-xl">{step.title}</h3>
              <p className="mx-auto mt-2 max-w-xs text-sm leading-relaxed text-ink-700/80">{step.body}</p>
            </li>
          ))}
        </ol>
      </Container>
    </section>
  )
}

function Faq() {
  return (
    <section id="faqs" className="scroll-mt-16 border-t border-paper-300 py-20 sm:py-28">
      <Container className="grid gap-10 lg:grid-cols-[1fr_1.4fr] lg:gap-20">
        <div>
          <Eyebrow>FAQs</Eyebrow>
          <h2 className="mt-4 text-4xl font-light tracking-[-0.02em] sm:text-5xl">Questions, answered</h2>
          <p className="mt-4 max-w-sm text-sm leading-relaxed text-ink-700/80">
            The things people ask before their first trek with us. Something else on your mind?
          </p>
          <div className="mt-6">
            <PillLink to={SITE_LINKS.faqs} dark>
              Explore more questions
            </PillLink>
          </div>
        </div>
        <FaqList items={FAQS.slice(0, 5)} />
      </Container>
    </section>
  )
}

function ClosingCall() {
  return (
    // Full-bleed, square edges: the photo runs straight into the footer.
    <section>
      <div className="relative isolate overflow-hidden text-white">
        <img src="/hero-4.jpg" alt="" className="parallax absolute inset-0 -z-20 size-full object-cover" />
        <div className="absolute inset-0 -z-10 bg-ink-950/50" aria-hidden="true" />
        <Container className="py-24 text-center sm:py-36">
          <h2 className="mx-auto max-w-3xl text-4xl leading-[1.1] font-light tracking-[-0.02em] sm:text-6xl">
            The ridge is closer than you think.
          </h2>
          <p className="mx-auto mt-5 max-w-md text-sm leading-relaxed text-white/80 sm:text-base">
            Pick a date, read about your guide, and hold your seat. Come with friends, or come alone and meet nine more.
          </p>
          <div className="mt-8">
            <PillLink to="/treks">Find your departure</PillLink>
          </div>
        </Container>
      </div>
    </section>
  )
}

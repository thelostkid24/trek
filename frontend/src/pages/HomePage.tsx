import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState, type CSSProperties, type MouseEvent, type ReactNode } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { listCatalog, trekQueryOptions, type CatalogTrek } from '../api/catalog.ts'
import { messageFor } from '../auth/errorMessages.ts'
import { FaqList } from '../components/FaqList.tsx'
import { FAQS } from '../lib/faqs.ts'
import { rupees, shortRange } from '../lib/format.ts'
import { revealClass, staggerStyle, useInView } from '../lib/reveal.ts'
import { Logo } from '../components/Logo.tsx'
import { Ridgeline } from '../components/Ridgeline.tsx'
import { SITE_LINKS } from '../lib/siteLinks.ts'
import { Seo } from '../components/Seo.tsx'
import hero1 from '../assets/hero/hero-1.jpg'
import hero2 from '../assets/hero/hero-2.jpg'
import hero3 from '../assets/hero/hero-3.jpg'
import hero4 from '../assets/hero/hero-4.jpg'

/** Same query (and cache) as /treks, so opening "See all treks" from here is instant. */
const useCatalog = () => useQuery({ queryKey: ['public-catalog'], queryFn: listCatalog })

export function HomePage() {
  return (
    <div className="bg-paper-50 font-grotesk text-ink-900">
      <Seo path="/" />
      <Hero />
      <Destinations />
      <HowItWorks />
      <WhyChooseUs />
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

function Hero() {
  return (
    <section>
      <div className="relative isolate overflow-hidden bg-ink-900 text-white">
        <HeroSlides />
        <div className="absolute inset-0 -z-10 bg-gradient-to-r from-ink-950/75 via-ink-950/30 to-transparent" aria-hidden="true" />
        <div className="absolute inset-x-0 bottom-0 -z-10 h-2/3 bg-gradient-to-t from-ink-950/80 to-transparent" aria-hidden="true" />

        <Container className="grid min-h-[44rem] items-end gap-10 pt-28 pb-16 lg:min-h-svh lg:pb-20">
          <div>
            {/* The page's one h1: what search engines read as the site's headline. */}
            <h1
              className="fade-rise max-w-2xl text-[2.6rem] leading-[1.05] font-light tracking-[-0.02em] sm:text-6xl lg:text-7xl"
              style={{ '--d': '550ms' } as CSSProperties}
            >
              Small-batch treks with guides you choose.
            </h1>
            <p className="fade-rise mt-5 max-w-md text-sm leading-relaxed text-white/75 sm:mt-6 sm:text-base" style={{ '--d': '700ms' } as CSSProperties}>
              Small-batch treks led by certified mountaineers you choose by name, with their credentials and reviews in front of you before you pay.
            </p>
            <div className="fade-rise mt-8 flex flex-wrap items-center gap-5" style={{ '--d': '850ms' } as CSSProperties}>
              <PillLink to="/treks">Browse treks</PillLink>
            </div>
          </div>
        </Container>
      </div>
    </section>
  )
}

/**
 * Landing photos, in order; `place` is the small caption. They live in src/assets so every build gives them
 * hashed names: browsers keep them for a year and still see a new photo the moment it's deployed.
 * All four are free Unsplash photos (nika-tchokhonelidze, tim-foster, todd-diemer, vivek), scaled to 1600 px.
 * public/hero.jpg is a copy of hero-1 for the og:image; hero-4 is also the closing call's backdrop.
 * vite.config.ts preloads hero-1 from index.html on the home page, so it downloads alongside the app.
 */
const HERO_SLIDES: { src: string; place?: string }[] = [{ src: hero1 }, { src: hero2 }, { src: hero3 }, { src: hero4 }]

/** How long each photo holds before the next one fades in. */
const SLIDE_MS = 6000

/**
 * Crossfading hero photos that advance on their own, with a progress bar per photo, like a
 * streaming app's banner. The active bar's CSS animation is the timer: when it ends the next
 * photo comes in, and pausing it (hover, keyboard focus) pauses the rotation. Photos that fail
 * to load drop out, so a missing file just shortens the loop. With reduced motion there's no
 * autoplay or zoom; the bars still switch photos.
 * Until the first photo has loaded a dark skeleton pulses in its place; the other photos are only
 * requested after that, so on a slow connection they don't compete with the first.
 */
function HeroSlides() {
  const [firstLoaded, setFirstLoaded] = useState(false)
  const first = useRef<HTMLImageElement>(null)
  // A photo already in the browser cache can finish before React attaches onLoad.
  useEffect(() => {
    if (first.current?.complete && first.current.naturalWidth > 0) setFirstLoaded(true)
  }, [])
  const [failed, setFailed] = useState<string[]>([])
  const [index, setIndex] = useState(0)
  // The outgoing photo keeps its zoom while it fades, so it doesn't snap back mid-fade.
  const [prev, setPrev] = useState<number | null>(null)
  const [paused, setPaused] = useState(false)
  const slides = HERO_SLIDES.filter((s, i) => !failed.includes(s.src) && (i === 0 || firstLoaded))
  const active = slides.length > 0 ? index % slides.length : 0
  const show = (i: number) => {
    setPrev(active)
    setIndex(i)
  }

  return (
    <>
      <div
        className={`absolute inset-0 -z-30 bg-ink-800 transition-opacity duration-700 motion-safe:animate-pulse ${
          firstLoaded ? 'opacity-0' : 'opacity-100'
        }`}
        aria-hidden="true"
      />
      <div className="absolute inset-0 -z-20" aria-hidden="true">
        {slides.map((s, i) => (
          <img
            key={s.src}
            ref={i === 0 ? first : undefined}
            src={s.src}
            alt=""
            fetchPriority={i === 0 ? 'high' : 'low'}
            onLoad={i === 0 ? () => setFirstLoaded(true) : undefined}
            onError={() => {
              setFailed((f) => [...f, s.src])
              if (i === 0) setFirstLoaded(true)
            }}
            className={`absolute inset-0 size-full object-cover transition-opacity duration-700 ease-in-out ${
              i === active && firstLoaded ? 'opacity-100' : 'opacity-0'
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
 * Every listed trek as a row of square cards that slides sideways. A cover shares its
 * view-transition name with the trek page hero, so opening a card grows it into the hero. The trek is
 * fetched before navigating (usually already warm from hover) so the hero exists when the new page is snapshotted.
 */
function Destinations() {
  const catalog = useCatalog()
  const treks = catalog.data?.items ?? []
  const { ref, ...reveal } = useInView<HTMLUListElement>()

  return (
    <section id="treks" className="scroll-mt-16 py-20 sm:py-28">
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
          <ul className="-mx-5 mt-10 flex snap-x snap-mandatory scroll-px-5 gap-4 overflow-x-auto px-5 pb-2 [scrollbar-width:none] sm:-mx-10 sm:scroll-px-10 sm:px-10 [&::-webkit-scrollbar]:hidden" aria-busy="true" aria-label="Loading treks">
            {[0, 1, 2, 3, 4].map((i) => (
              <li key={i} className="aspect-square w-72 shrink-0 animate-pulse rounded-[1.5rem] bg-paper-200 sm:w-80" />
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
          <ul ref={ref} className="-mx-5 mt-10 flex snap-x snap-mandatory scroll-px-5 gap-4 overflow-x-auto px-5 pb-2 [scrollbar-width:none] sm:-mx-10 sm:scroll-px-10 sm:px-10 [&::-webkit-scrollbar]:hidden">
            {treks.map((t, i) => (
              <DestinationCard key={t.slug} trek={t} reveal={reveal} delay={i * 90} />
            ))}
          </ul>
        )}
      </Container>
    </section>
  )
}

function DestinationCard({
  trek,
  reveal,
  delay,
}: {
  trek: CatalogTrek
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
    <li style={staggerStyle(reveal, delay)} className={`w-72 shrink-0 snap-start sm:w-80 ${revealClass(reveal.inView)}`}>
      <Link
        to={to}
        viewTransition
        onPointerEnter={prefetch}
        onFocus={prefetch}
        onTouchStart={prefetch}
        onClick={(e) => void open(e)}
        className="group relative isolate flex aspect-square flex-col justify-between overflow-hidden rounded-[1.5rem] bg-brand-950 p-5 text-white"
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
          <h3 className="text-2xl leading-tight font-normal tracking-[-0.01em]">{trek.name}</h3>
          <p className="mt-2 text-xs text-white/75">
            {next ? `Next ${shortRange(next.start_date, next.end_date)}` : trek.season_label ?? 'Dates coming soon'}
            {from !== null && ` · from ${rupees(from)}`}
          </p>
        </div>
      </Link>
    </li>
  )
}

const STEPS: { title: string; body: ReactNode; icon: ReactNode }[] = [
  {
    title: 'Pick a trek',
    body: (
      <>
        You’ll find every departure listed in the{' '}
        <Link to="/treks" viewTransition className="underline decoration-laterite-400/60 underline-offset-4 hover:text-ink-900">
          All Treks
        </Link>{' '}
        section.
      </>
    ),
    // The brand mark itself, drawn large: the trek is where it starts.
    icon: <Logo onLight className="h-auto w-[4.5rem]" />,
  },
  {
    title: 'Choose your guide',
    body: 'See the months and dates each guide is leading the trek. Pick your date and guide, then open the departure for the details.',
    icon: (
      <StepIcon>
        <circle cx="10" cy="7" r="3" />
        <path d="M4 17c.8-3.2 3.1-5 6-5s5.2 1.8 6 5" strokeLinecap="round" />
      </StepIcon>
    ),
  },
  {
    title: 'Book your departure',
    body: 'Check the guide’s credentials and experience in detail, then book your seat and pay in one go.',
    icon: (
      <StepIcon>
        <rect x="3" y="4.5" width="14" height="12.5" rx="2" />
        <path d="M3 8.5h14M7 2.5v4M13 2.5v4M7.5 12.5l1.75 1.75L12.5 11" strokeLinecap="round" strokeLinejoin="round" />
      </StepIcon>
    ),
  },
]

function StepIcon({ children }: { children: ReactNode }) {
  return (
    <svg viewBox="0 0 20 20" className="size-11" fill="none" stroke="currentColor" strokeWidth="1.2" aria-hidden="true">
      {children}
    </svg>
  )
}

function HowItWorks() {
  const { ref, ...steps } = useInView<HTMLOListElement>()
  return (
    <section id="how-it-works" className="scroll-mt-16 py-20 sm:py-28">
      <Container>
        <div className="mx-auto max-w-3xl text-center">
          <div className="flex justify-center">
            <Eyebrow>How to book</Eyebrow>
          </div>
          <h2 className="mt-4 text-4xl font-light tracking-[-0.02em] sm:text-5xl">Book your trek in 3 simple steps</h2>
        </div>
        <ol ref={ref} className="relative mx-auto mt-14 grid max-w-6xl gap-12 md:mt-16 md:grid-cols-3 md:gap-8">
          {/* The trail between the circles draws itself left to right. */}
          <span
            aria-hidden="true"
            className={`absolute top-14 right-[16.6%] left-[16.6%] hidden h-px origin-left bg-laterite-400/50 transition-transform duration-[1600ms] ease-out md:block ${
              steps.inView ? 'scale-x-100' : 'scale-x-0'
            }`}
          />
          {STEPS.map((step, i) => (
            <li key={step.title} style={staggerStyle(steps, 200 + i * 250)} className={`group relative text-center ${revealClass(steps.inView)}`}>
              <span className="relative mx-auto flex size-28 items-center justify-center rounded-full border border-laterite-400 bg-paper-50 text-laterite-600 transition duration-300 group-hover:-translate-y-1">
                {step.icon}
              </span>
              <p className="mt-6 text-xs font-medium tracking-[0.2em] text-laterite-600 uppercase">Step {i + 1}</p>
              <h3 className="mt-3 text-2xl font-semibold tracking-[-0.01em]">{step.title}</h3>
              <p className="mx-auto mt-3 max-w-xs text-sm leading-relaxed text-ink-700/80">{step.body}</p>
            </li>
          ))}
        </ol>
        <p className="mx-auto mt-14 flex max-w-5xl flex-wrap items-center justify-center gap-x-3 gap-y-2 rounded-3xl border border-paper-300 px-6 py-4 text-center text-sm text-ink-700/80 sm:mt-16 sm:rounded-full">
          <svg viewBox="0 0 20 20" className="size-5 shrink-0 text-laterite-600" fill="none" stroke="currentColor" strokeWidth="1.5" aria-hidden="true">
            <circle cx="10" cy="7" r="3" />
            <path d="M4 17c.8-3.2 3.1-5 6-5s5.2 1.8 6 5" strokeLinecap="round" />
          </svg>
          <span>
            <strong className="font-semibold text-ink-900">Already know who you want to trek with?</strong> Pick your guide first, then
            choose a date they’re leading the trek you want.
          </span>
          <Link to="/guides" viewTransition className="font-semibold whitespace-nowrap text-laterite-600 hover:underline">
            Browse guides →
          </Link>
        </p>
      </Container>
    </section>
  )
}

/** Three numbers that set us apart, each with the promise behind it. */
const WHY_US = [
  { stat: '10', title: 'trekkers, maximum', body: 'Most trek companies take 25 to 30 people, sometimes even 40.' },
  {
    stat: '1',
    title: 'certified guide, chosen by you',
    body: 'See their profile and pick them by name before you pay. A co-guide joins every trek for additional support and safety.',
  },
  { stat: '100%', title: 'guides verified', body: 'Credentials, certificates and experience are strictly evaluated before listing.' },
]

function WhyChooseUs() {
  return (
    <section id="why-us" className="scroll-mt-16 py-20 sm:py-28">
      <Container>
        <h2 className="text-center text-4xl font-light tracking-[-0.02em] sm:text-5xl">Why choose The Empty Valley</h2>
        <ul className="mt-12 grid divide-y divide-paper-300 sm:mt-16 sm:grid-cols-3 sm:divide-x sm:divide-y-0">
          {WHY_US.map((item) => (
            <li key={item.title} className="px-6 py-8 text-center first:pt-0 last:pb-0 sm:py-0">
              <p className="text-7xl leading-none font-extralight tracking-[-0.03em] text-laterite-400 sm:text-8xl">
                {item.stat}
              </p>
              <h3 className="mt-6 text-lg font-semibold">{item.title}</h3>
              <p className="mx-auto mt-3 max-w-xs text-sm leading-relaxed text-ink-700/80">{item.body}</p>
            </li>
          ))}
        </ul>
      </Container>
    </section>
  )
}

function Faq() {
  return (
    <section id="faqs" className="scroll-mt-16 py-20 sm:py-28">
      <Container className="grid gap-10 lg:grid-cols-[1fr_1.4fr] lg:gap-20">
        <div>
          <Eyebrow>FAQs</Eyebrow>
          <h2 className="mt-4 text-4xl font-light tracking-[-0.02em] sm:text-5xl">Your queries</h2>
          <p className="mt-4 max-w-sm text-sm leading-relaxed text-ink-700/80">
            You can find all your doubts answered here.
          </p>
          <div className="mt-6">
            <PillLink to={SITE_LINKS.faqs} dark>
              Explore more queries!
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
        <img src={hero4} alt="" loading="lazy" className="parallax absolute inset-0 -z-20 size-full object-cover" />
        <div className="absolute inset-0 -z-10 bg-ink-950/50" aria-hidden="true" />
        <Container className="py-24 text-center sm:py-36">
          <h2 className="mx-auto max-w-3xl text-4xl leading-[1.1] font-light tracking-[-0.02em] sm:text-6xl">
            The ridge is closer than you think.
          </h2>
          <p className="mx-auto mt-5 max-w-md text-sm leading-relaxed text-white/80 sm:text-base">
            Choose your trek, pick a date, select your guide, read about them and pay. You are good to go.
          </p>
          <div className="mt-8">
            <PillLink to="/treks">Find your departure</PillLink>
          </div>
        </Container>
      </div>
    </section>
  )
}

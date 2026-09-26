import { useQuery } from '@tanstack/react-query'
import { useEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react'
import { Link, Outlet, ScrollRestoration, useLocation } from 'react-router-dom'
import { getHealth } from '../api/client.ts'
import { PROFILE_PATH } from '../auth/useCompleteSignIn.ts'
import { useAuth } from '../auth/useAuth.ts'
import { Avatar } from './Avatar.tsx'
import { GUEST_SIGN_OUT_WARNING } from './booking/GuestNotice.tsx'
import { SITE_LINKS } from '../lib/siteLinks.ts'
import { useTheme } from '../lib/theme.ts'

const NAV = [
  { label: 'All Treks', href: '/treks' },
  { label: 'How it works', href: '/#how-it-works' },
  { label: 'Our Vision', href: SITE_LINKS.vision },
  { label: 'FAQs', href: '/#faqs' },
]

const FOOTER_LINKS = [
  { label: 'Cancellation policy', href: SITE_LINKS.cancellations },
  { label: 'Contact', href: SITE_LINKS.contact },
  { label: 'Terms', href: SITE_LINKS.terms },
  { label: 'Privacy', href: SITE_LINKS.privacy },
]

/** Hash links stay plain anchors so the browser scrolls; routes go through the router. */
function NavItem({
  href,
  className,
  onClick,
  children,
}: {
  href: string
  className: string
  onClick?: () => void
  children: ReactNode
}) {
  if (href.includes('#')) {
    return (
      <a href={href} onClick={onClick} className={className}>
        {children}
      </a>
    )
  }
  return (
    <Link to={href} viewTransition onClick={onClick} className={className}>
      {children}
    </Link>
  )
}

const isCurrent = (location: { pathname: string; hash: string }, href: string) =>
  href.startsWith('/#') ? location.pathname === '/' && location.hash === href.slice(1) : location.pathname === href

/** True once the page has scrolled past `offset` pixels. */
function useScrolledPast(offset: number) {
  const [past, setPast] = useState(() => window.scrollY > offset)
  useEffect(() => {
    const onScroll = () => setPast(window.scrollY > offset)
    onScroll()
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [offset])
  return past
}

export function Layout() {
  const location = useLocation()
  // The phone menu belongs to the page it was opened on, so navigating closes it.
  const [menuOpenOn, setMenuOpenOn] = useState<string | null>(null)
  const menuOpen = menuOpenOn === location.key
  const setMenuOpen = (open: boolean) => setMenuOpenOn(open ? location.key : null)
  // Every page gets the same frosted paper header; only the landing page's top lets it float clear over the hero photo.
  const home = location.pathname === '/'
  const scrolled = useScrolledPast(24)
  const tone: HeaderTone = menuOpen ? 'dark' : home && !scrolled ? 'clear' : 'light'

  return (
    <div className="flex min-h-dvh flex-col">
      <header
        className={`site-header top-0 z-20 font-grotesk transition-[background-color,color,box-shadow] duration-500 ${
          home ? 'fixed inset-x-0' : 'sticky'
        } ${HEADER_TONES[tone]}`}
      >
        <div className="mx-auto flex h-16 max-w-[90rem] items-center justify-between gap-6 px-5 sm:px-10">
          <div className="flex items-center gap-10">
            <Link to="/" viewTransition className="flex items-center gap-2 text-[1.2rem] font-semibold tracking-tight">
              <Peak className="size-6" />
              The Empty Valley
            </Link>
            <nav className="hidden items-center gap-7 text-sm md:flex">
              {NAV.map((item) => (
                <NavItem
                  key={item.label}
                  href={item.href}
                  className={`relative py-1 transition-opacity after:absolute after:inset-x-0 after:-bottom-0.5 after:h-px after:origin-left after:scale-x-0 after:bg-current after:transition-transform after:duration-300 hover:opacity-100 hover:after:scale-x-100 ${
                    isCurrent(location, item.href) ? 'opacity-100 after:scale-x-100' : 'opacity-75'
                  }`}
                >
                  {item.label}
                </NavItem>
              ))}
            </nav>
          </div>
          <div className="flex items-center gap-3 text-sm sm:gap-5">
            <ThemeToggle />
            <AccountLink tone={tone} />
            <button
              type="button"
              aria-label={menuOpen ? 'Close menu' : 'Open menu'}
              aria-expanded={menuOpen}
              aria-controls="site-menu"
              onClick={() => setMenuOpen(!menuOpen)}
              className="-mr-2 flex size-10 items-center justify-center opacity-90 hover:opacity-100 md:hidden"
            >
              <svg viewBox="0 0 24 24" className="size-5" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden="true">
                {menuOpen ? <path d="M6 6l12 12M18 6 6 18" /> : <path d="M4 7h16M4 12h16M4 17h16" />}
              </svg>
            </button>
          </div>
        </div>
        {menuOpen && (
          <nav id="site-menu" className="border-t border-white/10 px-5 pb-4 text-base sm:px-10 md:hidden">
            {NAV.map((item, i) => (
              <NavItem
                key={item.label}
                href={item.href}
                onClick={() => setMenuOpen(false)}
                className="block py-3 hover:text-white"
              >
                <span className="fade-rise inline-block" style={{ '--d': `${i * 50}ms` } as CSSProperties}>
                  {item.label}
                </span>
              </NavItem>
            ))}
          </nav>
        )}
      </header>

      {/* Pages own their width so landing sections can run full-bleed. */}
      <main className="flex-1">
        <Outlet />
      </main>
      {/* New pages open at the top (so a trek card's cover can grow into the hero); Back restores. */}
      <ScrollRestoration />

      <Footer />
    </div>
  )
}

type HeaderTone = 'clear' | 'light' | 'dark'

const HEADER_TONES: Record<HeaderTone, string> = {
  clear: 'bg-transparent text-white',
  light: 'bg-paper-50/85 text-ink-900 shadow-[0_1px_0_rgb(0_0_0/0.06)] backdrop-blur-md',
  dark: 'bg-ink-900 text-white/85',
}

/** Sun/moon switch between the light and dark themes. */
function ThemeToggle() {
  const { theme, toggle } = useTheme()
  const dark = theme === 'dark'
  return (
    <button
      type="button"
      onClick={toggle}
      aria-label={dark ? 'Switch to light theme' : 'Switch to dark theme'}
      title={dark ? 'Light theme' : 'Dark theme'}
      className="flex size-9 items-center justify-center rounded-full opacity-80 transition hover:opacity-100"
    >
      <svg viewBox="0 0 24 24" className="size-[1.15rem]" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" aria-hidden="true">
        {dark ? (
          <>
            <circle cx="12" cy="12" r="4" />
            <path d="M12 2.5v2M12 19.5v2M4.6 4.6l1.4 1.4M18 18l1.4 1.4M2.5 12h2M19.5 12h2M4.6 19.4 6 18M18 6l1.4-1.4" />
          </>
        ) : (
          <path d="M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5Z" strokeLinejoin="round" />
        )}
      </svg>
    </button>
  )
}

/** The wordmark's glyph: a flat-topped Sahyadri mesa beside a sharper peak. */
function Peak({ className = '' }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" className={className} fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden="true">
      <path d="M2 19 8.5 8h3L14 12l2.5-5L22 19Z" strokeLinejoin="round" />
      <path d="M2 19h20" strokeLinecap="round" />
    </svg>
  )
}

function Footer() {
  return (
    <footer className="overflow-hidden bg-ink-950 font-grotesk text-ink-400">
      <div className="mx-auto max-w-[90rem] px-5 pt-14 sm:px-10 sm:pt-20">
        <div className="grid gap-10 border-b border-white/10 pb-12 md:grid-cols-[1.4fr_1fr_1fr]">
          <div className="max-w-sm">
            <p className="text-2xl leading-snug font-light text-white/90 sm:text-3xl">
              Ten people, one guide you chose, and a mountain that still feels empty.
            </p>
            <a
              href={SITE_LINKS.leadATrek}
              target="_blank"
              rel="noopener noreferrer"
              className="group mt-6 inline-flex items-center gap-3 rounded-full border border-white/20 py-1.5 pr-1.5 pl-5 text-sm text-white/90 transition hover:border-white/50"
            >
              Lead a trek with us
              <span className="flex size-8 items-center justify-center rounded-full bg-white text-ink-950 transition-transform duration-300 group-hover:rotate-45">
                ↗
              </span>
            </a>
          </div>
          <FooterColumn title="Explore" links={[{ label: 'All treks', href: '/treks' }, ...NAV.slice(1)]} />
          <FooterColumn title="The fine print" links={FOOTER_LINKS} />
        </div>
        <div className="flex flex-col gap-2 py-6 text-xs sm:flex-row sm:items-center sm:justify-between">
          <p>Fair-trade, micro-batch trekking. Registered in Mumbai, Maharashtra.</p>
          {import.meta.env.DEV && <ApiStatus />}
        </div>
      </div>
      {/* The wordmark set huge and cut off by the page edge, like a ridge running out of frame. */}
      <p
        aria-hidden="true"
        className="-mb-[0.22em] text-center text-[15.5vw] leading-none font-semibold tracking-[-0.05em] whitespace-nowrap text-transparent select-none [background:linear-gradient(to_bottom,rgb(255_255_255/0.14),rgb(255_255_255/0.02))_text]"
      >
        The Empty Valley
      </p>
    </footer>
  )
}

function FooterColumn({ title, links }: { title: string; links: { label: string; href: string }[] }) {
  return (
    <nav aria-label={title}>
      <p className="text-xs tracking-[0.14em] text-white/45 uppercase">{title}</p>
      <ul className="mt-4 space-y-2.5 text-sm">
        {links.map((item) => (
          <li key={item.label}>
            <NavItem href={item.href} className="text-white/70 transition hover:text-white">
              {item.label}
            </NavItem>
          </li>
        ))}
      </ul>
    </nav>
  )
}

/** "Sign in" for visitors; once authenticated, a profile menu (My profile + Sign out). */
function AccountLink({ tone }: { tone: HeaderTone }) {
  const auth = useAuth()
  const location = useLocation()

  if (auth.status === 'loading') return <span className="w-12" aria-hidden />
  if (auth.status === 'anonymous') {
    const onAuthPage = location.pathname === '/login' || location.pathname === '/signup'
    return (
      <Link
        to="/login"
        state={onAuthPage ? undefined : { from: location.pathname + location.search }}
        className={`rounded-full border px-4 py-1.5 font-medium transition ${
          tone === 'light' ? 'border-ink-900/25 hover:bg-ink-900 hover:text-white' : 'border-white/40 hover:bg-white hover:text-ink-900'
        }`}
      >
        Sign in
      </Link>
    )
  }

  const { user } = auth
  const name = user.full_name?.split(' ')[0] ?? user.email ?? user.phone ?? 'Account'
  return (
    <span className="flex items-center gap-3">
      {user.role === 'ADMIN' && (
        <Link to="/admin" className="font-medium opacity-85 hover:opacity-100">
          Admin
        </Link>
      )}
      {user.role === 'GUIDE' && (
        <Link to="/guide" className="font-medium opacity-85 hover:opacity-100">
          Snow reports
        </Link>
      )}
      <ProfileMenu
        name={name}
        fullName={user.full_name ?? name}
        avatarUrl={user.avatar_url}
        links={user.role === 'TREKKER' ? [{ to: PROFILE_PATH, label: 'My profile' }] : []}
        onSignOut={() => {
          if (!user.guest || window.confirm(GUEST_SIGN_OUT_WARNING)) void auth.signOut()
        }}
      />
    </span>
  )
}

/** Avatar button that opens My profile and Sign out. Closes on outside click, Escape or navigation. */
function ProfileMenu({
  name,
  fullName,
  avatarUrl,
  links,
  onSignOut,
}: {
  name: string
  fullName: string
  avatarUrl: string | null
  links: { to: string; label: string }[]
  onSignOut: () => void
}) {
  const location = useLocation()
  const ref = useRef<HTMLDivElement>(null)
  const [openOn, setOpenOn] = useState<string | null>(null)
  const open = openOn === location.key

  useEffect(() => {
    if (!open) return
    const onClick = (e: PointerEvent) => {
      if (!ref.current?.contains(e.target as Node)) setOpenOn(null)
    }
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpenOn(null)
    }
    document.addEventListener('pointerdown', onClick)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('pointerdown', onClick)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls="profile-menu"
        onClick={() => setOpenOn(open ? null : location.key)}
        className="flex items-center gap-2 opacity-90 hover:opacity-100"
      >
        <Avatar url={avatarUrl} name={fullName} />
        <span className="hidden max-w-32 truncate font-medium sm:inline">{name}</span>
        <svg viewBox="0 0 20 20" className="size-4 opacity-60" fill="currentColor" aria-hidden="true">
          <path d="M5.5 7.5 10 12l4.5-4.5z" />
        </svg>
      </button>
      {open && (
        <div
          id="profile-menu"
          role="menu"
          className="absolute right-0 z-30 mt-2 w-48 overflow-hidden rounded-xl border border-paper-300 bg-paper-50 py-1 text-sm text-ink-900 shadow-lg"
        >
          {links.map((item) => (
            <Link
              key={item.to}
              to={item.to}
              role="menuitem"
              className={`block px-4 py-2.5 hover:bg-paper-100 ${location.pathname === item.to ? 'font-medium text-pine-700' : ''}`}
            >
              {item.label}
            </Link>
          ))}
          <button
            type="button"
            role="menuitem"
            onClick={() => {
              setOpenOn(null)
              onSignOut()
            }}
            className={`block w-full px-4 py-2.5 text-left text-ink-700 hover:bg-paper-100 ${links.length ? 'border-t border-paper-300' : ''}`}
          >
            Sign out
          </button>
        </div>
      )}
    </div>
  )
}

/** Tiny backend health indicator for local development; hidden in production builds. */
function ApiStatus() {
  const health = useQuery({ queryKey: ['health'], queryFn: getHealth })
  const ok = health.data?.status === 'ok' && health.data.database === 'up'
  const color = health.isPending ? 'bg-stone-400' : health.isError ? 'bg-red-500' : ok ? 'bg-brand-300' : 'bg-laterite-400'
  const label = health.isPending
    ? 'checking API…'
    : health.isError
      ? health.error.message
      : `API ${health.data.status}, database ${health.data.database}`

  return (
    <span className="inline-flex items-center gap-2 text-[0.7rem] text-ink-400">
      <span className={`size-2 rounded-full ${color}`} />
      {label}
    </span>
  )
}

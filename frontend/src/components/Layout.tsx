import { useQuery } from '@tanstack/react-query'
import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Link, Outlet, ScrollRestoration, useLocation, useMatches } from 'react-router-dom'
import { getHealth } from '../api/client.ts'
import { PROFILE_PATH } from '../auth/useCompleteSignIn.ts'
import { useAuth } from '../auth/useAuth.ts'
import { Avatar } from './Avatar.tsx'
import { GUEST_SIGN_OUT_WARNING } from './booking/GuestNotice.tsx'
import { SITE_LINKS } from '../lib/siteLinks.ts'

const NAV = [
  { label: 'All Treks', href: '/treks' },
  { label: 'How it works', href: '/#how-it-works' },
  { label: 'Our Vision', href: SITE_LINKS.vision },
  { label: 'FAQs', href: '/#faqs' },
]

const FOOTER_LINKS = [
  { label: 'FAQs', href: '/#faqs' },
  { label: 'Cancellation policy', href: SITE_LINKS.cancellations },
  { label: 'Our Vision', href: SITE_LINKS.vision },
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

export function Layout() {
  const location = useLocation()
  // The phone menu belongs to the page it was opened on, so navigating closes it.
  const [menuOpenOn, setMenuOpenOn] = useState<string | null>(null)
  const menuOpen = menuOpenOn === location.key
  const setMenuOpen = (open: boolean) => setMenuOpenOn(open ? location.key : null)
  // The account area's fixed sidebar runs to the bottom of the window; the footer sits beside it, not over it.
  const accountSidebar = useMatches().some((m) => (m.handle as { accountSidebar?: boolean } | undefined)?.accountSidebar)

  return (
    <div className="flex min-h-dvh flex-col">
      <header className="site-header sticky top-0 z-20 bg-ink-900 font-plex text-stone-300">
        <div className="mx-auto flex h-16 max-w-[90rem] items-center justify-between gap-6 px-5 sm:px-10">
          <div className="flex items-center gap-10">
            <Link to="/" viewTransition className="font-serif text-[1.35rem] tracking-tight text-white">
              Sahyātri
            </Link>
            <nav className="hidden items-center gap-6 text-sm md:flex">
              {NAV.map((item) => (
                <NavItem key={item.label} href={item.href} className={`hover:text-white ${isCurrent(location, item.href) ? 'text-white' : ''}`}>
                  {item.label}
                </NavItem>
              ))}
            </nav>
          </div>
          <div className="flex items-center gap-5 text-sm">
            <AccountLink />
            <button
              type="button"
              aria-label={menuOpen ? 'Close menu' : 'Open menu'}
              aria-expanded={menuOpen}
              aria-controls="site-menu"
              onClick={() => setMenuOpen(!menuOpen)}
              className="-mr-2 flex size-10 items-center justify-center text-stone-200 hover:text-white md:hidden"
            >
              <svg viewBox="0 0 24 24" className="size-5" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden="true">
                {menuOpen ? <path d="M6 6l12 12M18 6 6 18" /> : <path d="M4 7h16M4 12h16M4 17h16" />}
              </svg>
            </button>
          </div>
        </div>
        {menuOpen && (
          <nav id="site-menu" className="border-t border-white/10 px-5 pb-4 text-sm sm:px-10 md:hidden">
            {NAV.map((item) => (
              <NavItem key={item.label} href={item.href} onClick={() => setMenuOpen(false)} className="block py-3 hover:text-white">
                {item.label}
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

      <footer className={`bg-ink-950 font-plex text-ink-400 ${accountSidebar ? 'md:ml-60' : ''}`}>
        <div className="mx-auto flex max-w-[90rem] flex-col gap-6 px-5 py-10 text-sm sm:flex-row sm:justify-between sm:px-10">
          <div className="max-w-xs">
            <p className="font-serif text-lg text-stone-200">Sahyātri</p>
            <p className="mt-2 text-xs leading-relaxed">Fair-trade, micro-batch trekking. Registered in Mumbai, Maharashtra.</p>
            <ApiStatus />
          </div>
          <nav className="flex flex-wrap gap-x-6 gap-y-2 text-[0.8rem]">
            {FOOTER_LINKS.map((item) => (
              <a key={item.label} href={item.href} className="hover:text-stone-200">
                {item.label}
              </a>
            ))}
            <a href={SITE_LINKS.leadATrek} target="_blank" rel="noopener noreferrer" className="hover:text-stone-200">
              Lead a trek ↗
            </a>
          </nav>
        </div>
      </footer>
    </div>
  )
}

/** "Sign in" for visitors; once authenticated, a profile menu (My profile + Sign out). */
function AccountLink() {
  const auth = useAuth()
  const location = useLocation()

  if (auth.status === 'loading') return <span className="w-12" aria-hidden />
  if (auth.status === 'anonymous') {
    const onAuthPage = location.pathname === '/login' || location.pathname === '/signup'
    return (
      <Link
        to="/login"
        state={onAuthPage ? undefined : { from: location.pathname + location.search }}
        className="font-medium text-stone-200 hover:text-white"
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
        <Link to="/admin" className="font-medium text-stone-200 hover:text-white">
          Admin
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
        className="flex items-center gap-2 hover:text-white"
      >
        <Avatar url={avatarUrl} name={fullName} />
        <span className="hidden max-w-32 truncate font-medium text-stone-200 sm:inline">{name}</span>
        <svg viewBox="0 0 20 20" className="size-4 text-stone-400" fill="currentColor" aria-hidden="true">
          <path d="M5.5 7.5 10 12l4.5-4.5z" />
        </svg>
      </button>
      {open && (
        <div
          id="profile-menu"
          role="menu"
          className="absolute right-0 z-30 mt-2 w-48 overflow-hidden rounded-sm border border-paper-300 bg-paper-50 py-1 text-sm text-ink-900 shadow-lg"
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

/** Tiny backend health indicator — useful while the app is being built. */
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
    <span className="mt-3 inline-flex items-center gap-2 text-[0.7rem] text-ink-400">
      <span className={`size-2 rounded-full ${color}`} />
      {label}
    </span>
  )
}

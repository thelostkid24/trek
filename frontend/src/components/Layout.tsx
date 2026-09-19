import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, Outlet, useLocation } from 'react-router-dom'
import { getHealth } from '../api/client.ts'
import { PROFILE_PATH } from '../auth/useCompleteSignIn.ts'
import { useAuth } from '../auth/useAuth.ts'
import { Avatar } from './Avatar.tsx'
import { GUEST_SIGN_OUT_WARNING } from './booking/GuestNotice.tsx'
import { SITE_LINKS } from '../lib/siteLinks.ts'

const NAV = [
  { label: 'All Treks', href: '/#treks' },
  { label: 'How it works', href: '/#how-it-works' },
  { label: 'Our Vision', href: SITE_LINKS.vision },
  { label: 'FAQs', href: '/#faqs' },
]

const FOOTER_LINKS = [
  { label: 'FAQs', href: '/#faqs' },
  { label: 'Cancellations', href: SITE_LINKS.cancellations },
  { label: 'Our Vision', href: SITE_LINKS.vision },
  { label: 'Contact', href: SITE_LINKS.contact },
]

export function Layout() {
  const location = useLocation()
  // The phone menu belongs to the page it was opened on, so navigating closes it.
  const [menuOpenOn, setMenuOpenOn] = useState<string | null>(null)
  const menuOpen = menuOpenOn === location.key
  const setMenuOpen = (open: boolean) => setMenuOpenOn(open ? location.key : null)

  return (
    <div className="flex min-h-dvh flex-col">
      <header className="sticky top-0 z-20 bg-ink-900 font-plex text-stone-300">
        <div className="mx-auto flex h-16 max-w-[90rem] items-center justify-between gap-6 px-5 sm:px-10">
          <div className="flex items-center gap-10">
            <Link to="/" className="font-serif text-[1.35rem] tracking-tight text-white">
              Sahyātri
            </Link>
            <nav className="hidden items-center gap-6 text-sm md:flex">
              {NAV.map((item) => (
                <a
                  key={item.label}
                  href={item.href}
                  className={`hover:text-white ${location.pathname === '/' && location.hash === item.href.slice(1) ? 'text-white' : ''}`}
                >
                  {item.label}
                </a>
              ))}
            </nav>
          </div>
          <div className="flex items-center gap-5 text-sm">
            <AccountLink />
            <a
              href={SITE_LINKS.leadATrek}
              className="hidden rounded-sm border border-stone-400/60 px-3.5 py-2 text-[0.8rem] text-white hover:border-white hover:bg-white/5 sm:inline-block"
            >
              Lead a trek ↗
            </a>
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
              <a key={item.label} href={item.href} onClick={() => setMenuOpen(false)} className="block py-3 hover:text-white">
                {item.label}
              </a>
            ))}
            <a href={SITE_LINKS.leadATrek} className="mt-2 block py-3 text-white sm:hidden">
              Lead a trek ↗
            </a>
          </nav>
        )}
      </header>

      {/* Pages own their width so landing sections can run full-bleed. */}
      <main className="flex-1">
        <Outlet />
      </main>

      <footer className="bg-ink-950 font-plex text-ink-400">
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
          </nav>
        </div>
      </footer>
    </div>
  )
}

/** "Sign in" for visitors; photo + first name (linking to the profile) and sign out once authenticated. */
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
  const profileLink = user.role === 'TREKKER'
  const identity = (
    <>
      <Avatar url={user.avatar_url} name={user.full_name ?? name} />
      <span className="hidden max-w-32 truncate font-medium text-stone-200 sm:inline">{name}</span>
    </>
  )
  return (
    <span className="flex items-center gap-3">
      {user.role === 'TREKKER' && (
        <Link to="/account/bookings" className="font-medium text-stone-200 hover:text-white">
          My trips
        </Link>
      )}
      {user.role === 'ADMIN' && (
        <Link to="/admin" className="font-medium text-stone-200 hover:text-white">
          Admin
        </Link>
      )}
      {profileLink ? (
        <Link to={PROFILE_PATH} className="flex items-center gap-2 hover:text-white" aria-label="Your profile">
          {identity}
        </Link>
      ) : (
        <span className="flex items-center gap-2">{identity}</span>
      )}
      <button
        type="button"
        onClick={() => {
          if (!user.guest || window.confirm(GUEST_SIGN_OUT_WARNING)) void auth.signOut()
        }}
        className="hover:text-white"
      >
        Sign out
      </button>
    </span>
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

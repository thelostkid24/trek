import { NavLink, Outlet } from 'react-router-dom'
import { PROFILE_PATH } from '../../auth/useCompleteSignIn.ts'
import { useAuth } from '../../auth/useAuth.ts'
import { Avatar } from '../../components/Avatar.tsx'
import { GUEST_SIGN_OUT_WARNING } from '../../components/booking/GuestNotice.tsx'
import { maskPhone } from '../../lib/format.ts'

const NAV = [
  { to: '/account/bookings', label: 'My treks' },
  { to: PROFILE_PATH, label: 'My profile' },
  { to: '/account/gear', label: 'Gear' },
]

const joinedFormat = new Intl.DateTimeFormat('en-IN', { month: 'long', year: 'numeric' })

/**
 * Trekker account area: slate sidebar (a tab strip on phones) around the account pages.
 * Rendered inside <RequireAuth role="TREKKER">. The `account-area` class restyles form fields (index.css).
 */
export function AccountLayout() {
  const auth = useAuth()
  if (auth.status !== 'authenticated') return null
  const { user } = auth
  const name = user.full_name ?? 'Trekker'
  const signOut = () => {
    if (!user.guest || window.confirm(GUEST_SIGN_OUT_WARNING)) void auth.signOut()
  }

  return (
    <div className="account-area bg-paper-100 md:min-h-[calc(100dvh-4rem)]">
      {/* Fixed, not sticky: a sticky rail rides up with its container once the footer scrolls in. Layout offsets the footer (route handle `accountSidebar`). */}
      <aside className="bg-ink-900 text-stone-300 md:fixed md:top-16 md:bottom-0 md:left-0 md:flex md:w-60 md:flex-col">
        <div className="hidden px-6 pt-8 pb-6 md:block">
          <p className="font-display text-xl text-stone-100">Sahyātri</p>
          <p className="mt-1 text-xs text-ink-400">Trekker since {joinedFormat.format(new Date(user.created_at))}</p>
        </div>

        <nav aria-label="Account" className="flex overflow-x-auto md:block">
          {NAV.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                `group flex shrink-0 items-center gap-3 border-b-2 px-5 py-3 text-sm transition md:border-b-0 md:border-l-2 md:px-6 ${
                  isActive
                    ? 'border-pine-400 bg-ink-800 text-stone-100'
                    : 'border-transparent text-stone-400 hover:bg-ink-800/60 hover:text-stone-200'
                }`
              }
            >
              {({ isActive }) => (
                <>
                  <span
                    aria-hidden="true"
                    className={`hidden size-1.5 rounded-full md:block ${isActive ? 'bg-pine-400' : 'bg-ink-400/60'}`}
                  />
                  {item.label}
                </>
              )}
            </NavLink>
          ))}
        </nav>

        <div className="mt-auto hidden border-t border-ink-700 px-6 py-5 md:block">
          <div className="flex items-center gap-3">
            <Avatar url={user.avatar_url} name={name} className="ring-ink-700!" />
            <div className="min-w-0">
              <p className="truncate text-sm text-stone-100">{name}</p>
              <p className="truncate text-xs text-ink-400">
                {user.phone ? maskPhone(user.phone) : (user.email ?? 'Guest account')}
              </p>
            </div>
          </div>
          <button type="button" onClick={signOut} className="mt-3 text-xs text-ink-400 hover:text-stone-200">
            Sign out
          </button>
        </div>
      </aside>

      <div className="min-w-0 md:ml-60">
        <Outlet />
      </div>
    </div>
  )
}

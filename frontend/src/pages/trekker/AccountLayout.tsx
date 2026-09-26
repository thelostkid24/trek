import { Outlet } from 'react-router-dom'
import { PROFILE_PATH } from '../../auth/useCompleteSignIn.ts'
import { useAuth } from '../../auth/useAuth.ts'
import { Avatar } from '../../components/Avatar.tsx'
import { PillTabs } from '../../components/PillTabs.tsx'
import { maskPhone } from '../../lib/format.ts'

const NAV = [
  { to: '/account/bookings', label: 'My treks' },
  { to: PROFILE_PATH, label: 'My profile' },
  { to: '/account/gear', label: 'Gear' },
]

const joinedFormat = new Intl.DateTimeFormat('en-IN', { month: 'long', year: 'numeric' })

/**
 * Trekker account area: the trekker's name over pill tabs, then the account page, on the landing page's paper.
 * Rendered inside <RequireAuth role="TREKKER">. The `account-area` class restyles form fields (index.css).
 */
export function AccountLayout() {
  const auth = useAuth()
  if (auth.status !== 'authenticated') return null
  const { user } = auth
  const name = user.full_name ?? 'Trekker'

  return (
    <div className="account-area min-h-[calc(100dvh-4rem)] bg-paper-50 text-ink-900">
      <div className="mx-auto max-w-[90rem] px-5 pt-10 sm:px-10 sm:pt-14">
        <div className="flex flex-col gap-6 sm:flex-row sm:items-end sm:justify-between">
          <div className="flex items-center gap-4">
            <Avatar url={user.avatar_url} name={name} size="md" className="ring-paper-50!" />
            <div className="min-w-0">
              <p className="flex items-center gap-2 text-xs font-medium tracking-[0.16em] text-pine-600 uppercase">
                <span className="h-px w-6 bg-current" aria-hidden="true" />
                Trekker since {joinedFormat.format(new Date(user.created_at))}
              </p>
              <p className="mt-1 truncate text-3xl font-light tracking-[-0.02em] sm:text-4xl">{name}</p>
              <p className="mt-0.5 truncate text-sm text-ink-700/70">
                {user.phone ? maskPhone(user.phone) : (user.email ?? 'Guest account')}
              </p>
            </div>
          </div>
          <PillTabs label="Account" tabs={NAV} />
        </div>
      </div>
      <div className="mx-auto max-w-[90rem] pb-10">
        <Outlet />
      </div>
    </div>
  )
}

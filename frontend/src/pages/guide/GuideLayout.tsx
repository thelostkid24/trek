import { Link, Outlet } from 'react-router-dom'
import { useAuth } from '../../auth/useAuth.ts'
import { Avatar } from '../../components/Avatar.tsx'
import { PillTabs } from '../../components/PillTabs.tsx'

const NAV = [
  { to: '/guide/profile', label: 'Profile' },
  { to: '/guide/reviews', label: 'Reviews' },
  { to: '/guide/questions', label: 'Questions' },
  { to: '/guide/availability', label: 'Availability' },
  { to: '/guide/reports', label: 'Snow reports' },
]

/**
 * Guide area (docs/TRD.md §7.21): the guide's name with pill tabs beneath, like the trekker account area. Rendered
 * inside <RequireAuth role="GUIDE">. The `account-area` class gives form fields the paper style (index.css).
 */
export function GuideLayout() {
  const auth = useAuth()
  if (auth.status !== 'authenticated') return null
  const { user } = auth
  const name = user.full_name ?? 'Guide'

  return (
    <div className="account-area min-h-[calc(100dvh-4rem)] bg-paper-50 text-ink-900">
      <div className="mx-auto max-w-[90rem] px-5 pt-10 sm:px-10 sm:pt-14">
        <div className="flex flex-col gap-6">
          <div className="flex items-center gap-4">
            <Avatar url={user.avatar_url} name={name} size="md" className="ring-paper-50!" />
            <div className="min-w-0">
              <p className="flex items-center gap-2 text-xs font-medium tracking-[0.16em] text-pine-600 uppercase">
                <span className="h-px w-6 bg-current" aria-hidden="true" />
                Mountain guide
              </p>
              <p className="mt-1 truncate text-3xl font-light tracking-[-0.02em] sm:text-4xl">{name}</p>
              <Link to={`/guides/${user.id}`} className="mt-0.5 inline-block text-sm text-pine-700 hover:text-pine-900">
                View your public profile →
              </Link>
            </div>
          </div>
          <PillTabs label="Guide" tabs={NAV} />
        </div>
      </div>
      <div className="mx-auto max-w-[90rem] pb-10">
        <Outlet />
      </div>
    </div>
  )
}

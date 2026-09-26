import { Outlet } from 'react-router-dom'
import { PillTabs } from '../../components/PillTabs.tsx'

const TABS = [
  { to: '/admin', label: 'Departures', end: true },
  { to: '/admin/tracks', label: 'Tracks', end: false },
  { to: '/admin/content', label: 'Page content', end: false },
  { to: '/admin/guides', label: 'Guides', end: false },
  { to: '/admin/insights', label: 'Insights', end: false },
]

/** /admin/* — rendered inside <RequireAuth role="ADMIN">. */
export function AdminLayout() {
  return (
    <div className="mx-auto max-w-5xl px-5 py-10 sm:px-10 sm:py-14">
      <div className="flex flex-col gap-6 sm:flex-row sm:items-end sm:justify-between">
        <h1 className="text-3xl font-light tracking-[-0.02em] sm:text-4xl">Admin</h1>
        <PillTabs label="Admin sections" tabs={TABS} />
      </div>
      <div className="mt-8 space-y-5">
        <Outlet />
      </div>
    </div>
  )
}

import { NavLink, Outlet } from 'react-router-dom'

const TABS = [
  { to: '/admin', label: 'Departures', end: true },
  { to: '/admin/tracks', label: 'Tracks', end: false },
  { to: '/admin/content', label: 'Page content', end: false },
  { to: '/admin/guides', label: 'Guides', end: false },
]

/** /admin/* — rendered inside <RequireAuth role="ADMIN">. */
export function AdminLayout() {
  return (
    <div className="mx-auto max-w-5xl px-4 py-6 sm:py-10">
      <h1 className="font-display text-2xl font-semibold">Admin</h1>
      <nav className="mt-4 flex gap-1 border-b border-stone-200" aria-label="Admin sections">
        {TABS.map((tab) => (
          <NavLink
            key={tab.to}
            to={tab.to}
            end={tab.end}
            className={({ isActive }) =>
              `-mb-px border-b-2 px-3 py-2 text-sm font-medium ${
                isActive ? 'border-brand-700 text-brand-900' : 'border-transparent text-stone-500 hover:text-stone-800'
              }`
            }
          >
            {tab.label}
          </NavLink>
        ))}
      </nav>
      <div className="mt-6 space-y-5">
        <Outlet />
      </div>
    </div>
  )
}

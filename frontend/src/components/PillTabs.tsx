import { NavLink } from 'react-router-dom'

/** Section tabs as a segmented pill, the same control as the landing page's trek finder. */
export function PillTabs({ label, tabs }: { label: string; tabs: { to: string; label: string; end?: boolean }[] }) {
  return (
    <nav
      aria-label={label}
      className="-mx-5 overflow-x-auto px-5 [scrollbar-width:none] sm:mx-0 sm:px-0 [&::-webkit-scrollbar]:hidden"
    >
      <div className="inline-flex gap-1 rounded-full bg-paper-200/70 p-1 text-sm">
        {tabs.map((tab) => (
          <NavLink
            key={tab.to}
            to={tab.to}
            end={tab.end}
            className={({ isActive }) =>
              `shrink-0 rounded-full px-5 py-2 font-medium whitespace-nowrap transition ${
                isActive ? 'bg-white text-ink-900 shadow-sm' : 'text-ink-700/70 hover:text-ink-900'
              }`
            }
          >
            {tab.label}
          </NavLink>
        ))}
      </div>
    </nav>
  )
}

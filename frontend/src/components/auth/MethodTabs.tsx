export type AuthMethodTab = 'email' | 'phone'

const TABS: { id: AuthMethodTab; label: string }[] = [
  { id: 'email', label: 'Email' },
  { id: 'phone', label: 'Mobile number' },
]

export function MethodTabs({ value, onChange }: { value: AuthMethodTab; onChange: (tab: AuthMethodTab) => void }) {
  return (
    <div role="tablist" className="grid grid-cols-2 rounded-sm bg-paper-200 p-1 text-sm">
      {TABS.map((tab) => (
        <button
          key={tab.id}
          type="button"
          role="tab"
          aria-selected={value === tab.id}
          onClick={() => onChange(tab.id)}
          className={`rounded-sm px-3 py-1.5 font-medium transition ${
            value === tab.id ? 'bg-paper-50 text-pine-700 shadow-sm' : 'text-ink-700/75 hover:text-ink-900'
          }`}
        >
          {tab.label}
        </button>
      ))}
    </div>
  )
}

import type { ReactNode } from 'react'

/** Marks a dashboard piece whose feature isn't built yet (docs/TRD.md §7.10). */
export function ComingSoon({ className = '' }: { className?: string }) {
  return (
    <span
      className={`inline-flex rounded-(--field-radius) border border-paper-300 bg-paper-200 px-2 py-0.5 text-[11px] font-medium text-stone-500 ${className}`}
    >
      Coming soon
    </span>
  )
}

/** Small label over a value, as in the trip cards. `dark` is for the slate card. */
export function Fact({ label, children, dark = false }: { label: string; children: ReactNode; dark?: boolean }) {
  return (
    <div>
      <p className={`text-xs ${dark ? 'text-ink-400' : 'text-stone-500'}`}>{label}</p>
      <p className={`mt-1 text-sm ${dark ? 'text-stone-100' : 'text-stone-900'}`}>{children}</p>
    </div>
  )
}

/** Paper card used for trip arrangements and gear. */
export function PaperCard({ className = '', children }: { className?: string; children: ReactNode }) {
  return <div className={`rounded-(--field-radius) border border-paper-300 bg-paper-50 p-4 ${className}`}>{children}</div>
}

/** Chips that pick one upcoming trek (My treks, Gear). */
export function TrekPicker<T extends { id: string }>({
  items,
  selectedId,
  onSelect,
  label,
  sub,
}: {
  items: T[]
  selectedId: string | undefined
  onSelect: (id: string) => void
  label: (item: T) => string
  sub: (item: T) => string
}) {
  return (
    <div className="flex gap-2 overflow-x-auto pb-1" role="tablist">
      {items.map((item) => {
        const active = item.id === selectedId
        return (
          <button
            key={item.id}
            type="button"
            role="tab"
            aria-selected={active}
            onClick={() => onSelect(item.id)}
            className={`shrink-0 rounded-(--field-radius) border px-4 py-2 text-left transition ${
              active
                ? 'border-ink-900 bg-ink-900 text-stone-100'
                : 'border-paper-300 bg-paper-50 text-stone-800 hover:border-pine-600'
            }`}
          >
            <span className="block text-sm">{label(item)}</span>
            <span className={`block text-xs ${active ? 'text-ink-400' : 'text-stone-500'}`}>{sub(item)}</span>
          </button>
        )
      })}
    </div>
  )
}

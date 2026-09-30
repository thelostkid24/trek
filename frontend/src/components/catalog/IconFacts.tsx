import type { ReactNode } from 'react'

/** Line icons for fact rows (24×24, drawn with the current colour). */
const ICONS = {
  calendar: (
    <>
      <rect x="3.5" y="5" width="17" height="15" rx="2" />
      <path d="M3.5 10h17M8 3v4M16 3v4" />
    </>
  ),
  clock: (
    <>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M12 7.5V12l3 2" />
    </>
  ),
  mountain: <path d="M2.5 19.5 9.5 7l4 7 2.5-4 5.5 9.5z" />,
  summit: (
    <>
      <path d="M2.5 20 10 8l3.5 5.5L16 10l5.5 10z" />
      <path d="M10 8V3.5l4 1.5-4 1.5" />
    </>
  ),
  gauge: <path d="M4 19V14M9 19V10M14 19V7M19 19V4" />,
  star: <path d="m12 3.5 2.6 5.3 5.9.9-4.3 4.1 1 5.8L12 16.9l-5.2 2.7 1-5.8-4.3-4.1 5.9-.9z" />,
  speech: <path d="M4 5.5h16v10H9.5L5 19.5v-4H4z" />,
  pin: (
    <>
      <path d="M12 21s7-6.2 7-11.5a7 7 0 1 0-14 0C5 14.8 12 21 12 21Z" />
      <circle cx="12" cy="9.5" r="2.5" />
    </>
  ),
  route: (
    <>
      <circle cx="6" cy="18" r="2" />
      <circle cx="18" cy="6" r="2" />
      <path d="M8 18h7.5a3 3 0 0 0 0-6h-7a3 3 0 0 1 0-6H16" />
    </>
  ),
  locker: (
    <>
      <rect x="5" y="3.5" width="14" height="17" rx="1.5" />
      <path d="M5 11h14M10 7.5h4M10 14.5h4" />
    </>
  ),
  backpack: (
    <>
      <path d="M7 8a5 5 0 0 1 10 0v11.5a1 1 0 0 1-1 1H8a1 1 0 0 1-1-1z" />
      <path d="M9.5 8V6.5a2.5 2.5 0 0 1 5 0V8M7 13h10M10 13v3h4v-3" />
    </>
  ),
} satisfies Record<string, ReactNode>

export type FactIcon = keyof typeof ICONS

export type Fact = { label: string; value: string; icon: FactIcon }

/**
 * Facts as an icon beside a small label and its value, no boxes. The one look for fact rows across the site: trek
 * facts, guide stats and the guide card. `className` sets the layout (a grid by default).
 */
export function IconFacts({ facts, className = 'grid grid-cols-2 gap-x-6 gap-y-5 sm:grid-cols-3' }: { facts: Fact[]; className?: string }) {
  return (
    <dl className={className}>
      {facts.map((f) => (
        <div key={f.label} className="flex items-start gap-3">
          <svg
            viewBox="0 0 24 24"
            className="mt-0.5 size-6 shrink-0 text-brand-800"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.6"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
          >
            {ICONS[f.icon]}
          </svg>
          <div className="min-w-0">
            <dt className="text-[0.65rem] font-medium tracking-[0.12em] text-stone-500 uppercase">{f.label}</dt>
            <dd className="mt-0.5 font-semibold text-stone-900">{f.value}</dd>
          </div>
        </div>
      ))}
    </dl>
  )
}

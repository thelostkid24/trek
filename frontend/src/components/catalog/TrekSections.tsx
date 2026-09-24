import { useEffect, useState, type ReactNode } from 'react'
import { DIFFICULTY_LABEL, type ContentItem, type RefundTier, type TrackDetail } from '../../api/catalog.ts'
import { addDays, dayLabel, feet, rupees } from '../../lib/format.ts'

// The trek page's sections (docs/TRD.md §7.7, §7.11): small, mostly static pieces the page stacks.

/** "OVERVIEW" in laterite small caps, with an optional note on the right. */
export function SectionLabel({ id, children, aside }: { id?: string; children: ReactNode; aside?: ReactNode }) {
  return (
    <div className="flex items-baseline justify-between gap-3">
      <h2 id={id} className="text-xs font-semibold tracking-[0.16em] text-laterite-600 uppercase">
        {children}
      </h2>
      {aside && <span className="text-xs text-stone-500">{aside}</span>}
    </div>
  )
}

/** A page section that tabs can jump to; clears the sticky header and tab bar. */
export function TrekSection({ id, label, aside, children }: { id: string; label: string; aside?: ReactNode; children: ReactNode }) {
  return (
    <section id={id} aria-labelledby={`${id}-heading`} className="scroll-mt-36 border-t border-paper-300 pt-8 first:border-0 first:pt-0">
      <SectionLabel id={`${id}-heading`} aside={aside}>
        {label}
      </SectionLabel>
      <div className="mt-4">{children}</div>
    </section>
  )
}

/** Duration, altitude, difficulty and the services, as cards. Facts nobody has stated are left out. */
export function FactGrid({ track }: { track: TrackDetail }) {
  const offloading =
    track.offloading === null
      ? null
      : track.offloading
        ? track.offloading_price_paise
          ? `Available · ${rupees(track.offloading_price_paise)}`
          : 'Available · paid'
        : 'Not available'
  const facts: [string, string | null][] = [
    ['Duration', `${track.duration_days} ${track.duration_days === 1 ? 'day' : 'days'}`],
    ['Maximum altitude', track.max_altitude_m ? feet(track.max_altitude_m) : null],
    ['Difficulty', DIFFICULTY_LABEL[track.difficulty]],
    track.pickup_drop ? ['Pickup and drop', track.pickup_drop] : ['Meeting point', track.meeting_point],
    ['Cloakroom', track.cloakroom === null ? null : track.cloakroom ? 'Available' : 'Not available'],
    ['Offloading', offloading],
  ]
  return (
    <dl className="grid grid-cols-2 gap-3 sm:grid-cols-3">
      {facts
        .filter((f): f is [string, string] => f[1] !== null)
        .map(([label, value]) => (
          <div key={label} className="rounded-xl bg-white/80 px-4 py-3 ring-1 ring-paper-300">
            <dt className="text-[0.65rem] font-medium tracking-[0.14em] text-stone-500 uppercase">{label}</dt>
            <dd className="mt-1 font-semibold text-stone-900">{value}</dd>
          </div>
        ))}
    </dl>
  )
}

/** Sticky jump links; the tab for the section in view is lit. */
export function TabBar({ tabs }: { tabs: { id: string; label: string }[] }) {
  const [active, setActive] = useState(tabs[0]?.id)

  useEffect(() => {
    const observer = new IntersectionObserver(
      (entries) => {
        const visible = entries.filter((e) => e.isIntersecting)
        if (visible.length > 0) setActive(visible[0].target.id)
      },
      // A section counts as current while its top is in the upper part of the window.
      { rootMargin: '-140px 0px -60% 0px' },
    )
    tabs.forEach((t) => {
      const el = document.getElementById(t.id)
      if (el) observer.observe(el)
    })
    return () => observer.disconnect()
  }, [tabs])

  return (
    <nav aria-label="Trek sections" className="sticky top-16 z-10 -mx-4 bg-paper-50/95 px-4 py-2 backdrop-blur sm:mx-0 sm:px-0">
      <ul className="flex overflow-x-auto rounded-xl bg-brand-900 p-1 text-sm [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
        {tabs.map((t) => (
          <li key={t.id} className="shrink-0">
            <a
              href={`#${t.id}`}
              aria-current={active === t.id ? 'location' : undefined}
              onClick={() => setActive(t.id)}
              className={`block rounded-lg px-4 py-2 font-medium whitespace-nowrap transition ${
                active === t.id ? 'bg-paper-50 text-stone-900' : 'text-brand-50 hover:bg-white/10'
              }`}
            >
              {t.label}
            </a>
          </li>
        ))}
      </ul>
    </nav>
  )
}

/** The description, one paragraph per blank-line-separated block. */
export function Overview({ text }: { text: string }) {
  return (
    <div className="max-w-2xl space-y-4 text-[1.05rem] leading-relaxed text-stone-700">
      {text
        .split(/\n\s*\n/)
        .map((p) => p.trim())
        .filter(Boolean)
        .map((p, i) => (
          <p key={i}>{p}</p>
        ))}
    </div>
  )
}

/** "What's included" and "What's not included" as two cards that open to their lists. */
export function Inclusions({ included, excluded, open = false }: { included: ContentItem[]; excluded: ContentItem[]; open?: boolean }) {
  return (
    <div className="grid items-start gap-3 sm:grid-cols-2">
      {included.length > 0 && <ListCard title="What's included" items={included} mark="✓" startOpen={open} />}
      {excluded.length > 0 && <ListCard title="What's not included" items={excluded} mark="–" startOpen={open} />}
    </div>
  )
}

function ListCard({ title, items, mark, startOpen }: { title: string; items: ContentItem[]; mark: string; startOpen: boolean }) {
  const [open, setOpen] = useState(startOpen)
  const id = `list-${title.replace(/\W+/g, '-').toLowerCase()}`
  return (
    <div className="rounded-xl bg-white/80 ring-1 ring-paper-300">
      <button
        type="button"
        aria-expanded={open}
        aria-controls={id}
        onClick={() => setOpen(!open)}
        className="flex w-full items-center justify-between gap-3 px-4 py-3 text-left"
      >
        <span>
          <span className="block text-xs font-semibold tracking-[0.14em] text-laterite-600 uppercase">{title}</span>
          <span className="text-sm text-stone-600">
            {items.length} {items.length === 1 ? 'item' : 'items'}
          </span>
        </span>
        <svg viewBox="0 0 20 20" className={`size-4 text-stone-500 transition ${open ? 'rotate-180' : ''}`} fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true">
          <path d="m5 8 5 5 5-5" />
        </svg>
      </button>
      <ul id={id} hidden={!open} className="divide-y divide-paper-200 px-4 pb-2">
        {items.map((item, i) => (
          <li key={i} className="flex gap-3 py-2.5 text-sm text-stone-700">
            <span className="shrink-0 font-semibold text-stone-900" aria-hidden="true">
              {mark}
            </span>
            {item.body}
          </li>
        ))}
      </ul>
    </div>
  )
}

/** Who decides to turn back (dark card), the safety checklist, then any notes under both. */
export function Safety({ callouts, checklist, notes }: { callouts: ContentItem[]; checklist: ContentItem[]; notes: ContentItem[] }) {
  const callout = callouts[0]
  return (
    <div className="space-y-3">
      <div className={`grid gap-3 ${callout && checklist.length > 0 ? 'sm:grid-cols-[1fr_1.4fr]' : ''}`}>
        {callout && (
          <div className="rounded-xl bg-brand-900 p-5 text-white">
            <p className="font-semibold">{callout.title}</p>
            <p className="mt-2 leading-relaxed text-brand-50">{callout.body}</p>
          </div>
        )}
        {checklist.length > 0 && (
          <ul className="divide-y divide-paper-200 rounded-xl bg-white/80 px-4 py-1 ring-1 ring-paper-300">
            {checklist.map((item, i) => (
              <li key={i} className="flex gap-3 py-3 text-sm text-stone-700">
                <span className="shrink-0 font-semibold text-stone-900" aria-hidden="true">
                  ✓
                </span>
                {item.body}
              </li>
            ))}
          </ul>
        )}
      </div>
      {notes.map((note, i) => (
        <p key={i} className="rounded-xl bg-paper-100 px-4 py-3 text-sm font-medium text-stone-800">
          {note.body}
        </p>
      ))}
    </div>
  )
}

/**
 * The refund tiers now in force, highest first (docs/TRD.md §7.6). The first card is dark. With `startDate` (a
 * departure page) each tier also says until when it applies.
 */
export function Cancellation({ tiers, startDate }: { tiers: RefundTier[]; startDate?: string }) {
  if (tiers.length === 0) return null
  return (
    <>
      <ul className="grid gap-3 sm:grid-cols-3">
        {tiers.map((t, i) => {
          const upper = i > 0 ? tiers[i - 1].min_days_before - 1 : null
          const when =
            i === 0
              ? `${t.min_days_before} days or more before`
              : t.min_days_before === 0
                ? `Under ${tiers[i - 1].min_days_before} days`
                : `${t.min_days_before} to ${upper} days before`
          const refund = t.refund_bps === 10_000 ? 'Full refund' : t.refund_bps === 0 ? 'No refund' : `${t.refund_bps / 100}% refund`
          const dark = i === 0
          return (
            <li key={t.min_days_before} className={`rounded-xl p-4 ${dark ? 'bg-brand-900 text-white' : 'bg-white/80 ring-1 ring-paper-300'}`}>
              <p className={`text-xs font-medium tracking-[0.12em] uppercase ${dark ? 'text-brand-100' : 'text-stone-500'}`}>{when}</p>
              <p className="mt-1 text-2xl font-semibold">{refund}</p>
              {startDate && t.min_days_before > 0 && (
                <p className={`mt-1 text-sm font-medium ${dark ? 'text-white' : 'text-stone-800'}`}>
                  until {dayLabel(addDays(startDate, -t.min_days_before))}
                </p>
              )}
              <p className={`mt-1 text-sm ${dark ? 'text-brand-100' : 'text-stone-600'}`}>
                {t.refund_bps === 0 && i > 0 ? `inside ${tiers[i - 1].min_days_before} days of the departure` : 'of what you paid'}
              </p>
            </li>
          )
        })}
      </ul>
      <p className="mt-3 text-sm text-stone-600">Cancel from My treks any time before the start date.</p>
    </>
  )
}

/** Questions that open one at a time. */
export function TrekFaqs({ items }: { items: ContentItem[] }) {
  const [open, setOpen] = useState<number | null>(null)
  return (
    <div className="border-t border-paper-300">
      {items.map((item, i) => {
        const isOpen = open === i
        return (
          <div key={i} className="border-b border-paper-300">
            <h3>
              <button
                type="button"
                aria-expanded={isOpen}
                aria-controls={`trek-faq-${i}`}
                onClick={() => setOpen(isOpen ? null : i)}
                className="flex w-full items-center justify-between gap-6 py-4 text-left font-semibold text-stone-900 hover:text-brand-800"
              >
                {item.title}
                <span className="shrink-0 text-lg font-normal text-stone-500" aria-hidden="true">
                  {isOpen ? '−' : '+'}
                </span>
              </button>
            </h3>
            <p id={`trek-faq-${i}`} hidden={!isOpen} className="max-w-2xl pb-5 whitespace-pre-line text-stone-700">
              {item.body}
            </p>
          </div>
        )
      })}
    </div>
  )
}

/** "Why choose us" cards: a big badge, a heading and a line or two. */
export function WhyUs({ items }: { items: ContentItem[] }) {
  return (
    <ul className="grid gap-3 sm:grid-cols-2">
      {items.map((item, i) => (
        <li key={i} className="rounded-xl bg-white/80 p-5 ring-1 ring-paper-300">
          {item.badge && <p className="font-serif text-4xl text-laterite-600">{item.badge}</p>}
          <p className="mt-2 font-semibold text-stone-900">{item.title}</p>
          <p className="mt-2 leading-relaxed text-stone-600">{item.body}</p>
        </li>
      ))}
    </ul>
  )
}

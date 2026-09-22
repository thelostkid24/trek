import { useState } from 'react'

/** Accordion of questions; the first one starts open. */
export function FaqList({ items }: { items: { q: string; a: string }[] }) {
  const [open, setOpen] = useState<number | null>(0)
  return (
    <div className="mt-6 border-t border-paper-300">
      {items.map((item, i) => {
        const isOpen = open === i
        return (
          <div key={item.q} className="border-b border-paper-300">
            <h3>
              <button
                type="button"
                aria-expanded={isOpen}
                aria-controls={`faq-${i}`}
                onClick={() => setOpen(isOpen ? null : i)}
                className="flex w-full items-center justify-between gap-6 py-4 text-left font-serif text-lg text-ink-900 hover:text-pine-700"
              >
                {item.q}
                <span className="shrink-0 font-plex text-lg text-pine-600" aria-hidden="true">
                  {isOpen ? '−' : '+'}
                </span>
              </button>
            </h3>
            <p id={`faq-${i}`} hidden={!isOpen} className="max-w-xl pb-5 text-sm leading-relaxed text-ink-700">
              {item.a}
            </p>
          </div>
        )
      })}
    </div>
  )
}

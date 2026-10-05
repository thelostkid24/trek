import { useId, useState } from 'react'
import { FAQ_PREVIEW, ShowMoreButton } from './ShowMoreButton.tsx'

/** Accordion of questions; the first one starts open. Past six, the rest wait behind "Show more" (still in the page). */
export function FaqList({ items }: { items: { q: string; a: string }[] }) {
  const [open, setOpen] = useState<number | null>(0)
  const [all, setAll] = useState(false)
  const listId = useId()
  const extra = items.length - FAQ_PREVIEW
  return (
    <div>
      <div id={listId} className="mt-6 border-t border-paper-300">
        {items.map((item, i) => {
          const isOpen = open === i
          return (
            <div key={item.q} hidden={!all && i >= FAQ_PREVIEW} className="border-b border-paper-300">
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
      {extra > 0 && <ShowMoreButton hidden={extra} expanded={all} controls={listId} onToggle={() => setAll(!all)} />}
    </div>
  )
}

import { useEffect, useId, useRef, useState } from 'react'
import { Link } from 'react-router-dom'

type Item = { label: string; to?: string; onSelect?: () => void }

const ITEM = 'block w-full px-4 py-2.5 text-left text-stone-600 hover:bg-paper-100 hover:text-stone-900'

/**
 * "⋯" button that opens a small menu of rarely used actions (cancelling a booking lives here on
 * purpose, so it takes a deliberate extra step). Closes on outside click, Escape or picking an item.
 */
export function MoreMenu({ label, items }: { label: string; items: Item[] }) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  const id = useId()

  useEffect(() => {
    if (!open) return
    const onPointer = (e: PointerEvent) => {
      if (!ref.current?.contains(e.target as Node)) setOpen(false)
    }
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('pointerdown', onPointer)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('pointerdown', onPointer)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        aria-label={label}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={id}
        onClick={() => setOpen(!open)}
        className={`flex size-9 items-center justify-center rounded-(--field-radius) border text-stone-600 transition hover:border-stone-400 hover:text-stone-900 ${
          open ? 'border-stone-400 bg-paper-100 text-stone-900' : 'border-paper-300 bg-paper-50'
        }`}
      >
        <svg viewBox="0 0 20 20" className="size-4" fill="currentColor" aria-hidden="true">
          <circle cx="4" cy="10" r="1.6" />
          <circle cx="10" cy="10" r="1.6" />
          <circle cx="16" cy="10" r="1.6" />
        </svg>
      </button>
      {open && (
        <div
          id={id}
          role="menu"
          onClick={() => setOpen(false)}
          className="absolute right-0 z-20 mt-1.5 min-w-48 overflow-hidden rounded-(--field-radius) border border-paper-300 bg-paper-50 py-1 text-sm shadow-lg"
        >
          {items.map((item) =>
            item.to ? (
              <Link key={item.label} to={item.to} role="menuitem" className={ITEM}>
                {item.label}
              </Link>
            ) : (
              <button key={item.label} type="button" role="menuitem" onClick={item.onSelect} className={ITEM}>
                {item.label}
              </button>
            ),
          )}
        </div>
      )}
    </div>
  )
}


import { useId, useState, type ReactNode } from 'react'

/**
 * A small tag whose "i" explains it in a tip above: shown while the pointer is over the tag or the "i" has keyboard
 * focus, and toggled by a tap on a phone. Moving the pointer away closes it, even after a click.
 */
export function InfoTag({
  icon,
  label,
  className = 'rounded bg-brand-50 px-1.5 text-[11px] leading-5 font-semibold text-brand-800',
  children,
}: {
  icon: ReactNode
  label: string
  /** The tag's look (colour, padding, type); the small green tag by default. */
  className?: string
  children: ReactNode
}) {
  const [open, setOpen] = useState(false)
  const tipId = useId()
  return (
    <span
      onMouseLeave={() => setOpen(false)}
      className={`group/tip relative inline-flex shrink-0 items-center gap-1 whitespace-nowrap ${className}`}
    >
      {icon}
      {label}
      <button
        type="button"
        aria-label={`What does ${label} mean?`}
        aria-describedby={tipId}
        aria-expanded={open}
        onClick={(e) => {
          e.preventDefault()
          setOpen((o) => !o)
        }}
        onBlur={() => setOpen(false)}
        className="ml-0.5 inline-flex size-[15px] cursor-help items-center justify-center rounded-full border-[1.2px] border-current font-serif text-[10px] leading-none font-bold italic hover:border-brand-800 hover:bg-brand-800 hover:text-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-600"
      >
        i
      </button>
      <span
        id={tipId}
        role="tooltip"
        className={`pointer-events-none absolute bottom-[calc(100%+10px)] left-1/2 z-20 w-59 -translate-x-1/2 rounded-lg bg-stone-950 px-3 py-2.5 text-left text-xs leading-[18px] font-normal whitespace-normal text-stone-100 transition-opacity after:absolute after:top-full after:left-1/2 after:-ml-1.5 after:border-6 after:border-transparent after:border-t-stone-950 group-hover/tip:opacity-100 group-has-[:focus-visible]/tip:opacity-100 ${
          open ? 'opacity-100' : 'opacity-0'
        }`}
      >
        {children}
      </span>
    </span>
  )
}

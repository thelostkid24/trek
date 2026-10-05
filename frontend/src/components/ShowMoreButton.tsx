/** How many questions a long FAQ list shows before "Show more". */
export const FAQ_PREVIEW = 6

/** "Show N more questions" / "Show fewer questions" under a long list, with a chevron that flips. */
export function ShowMoreButton({ hidden, expanded, controls, onToggle }: { hidden: number; expanded: boolean; controls: string; onToggle: () => void }) {
  return (
    <button
      type="button"
      aria-expanded={expanded}
      aria-controls={controls}
      onClick={onToggle}
      className="mt-5 inline-flex items-center gap-2 rounded-full px-4 py-2 text-sm font-medium text-ink-900 ring-1 ring-paper-300 transition hover:ring-pine-600"
    >
      {expanded ? 'Show fewer questions' : `Show ${hidden} more question${hidden === 1 ? '' : 's'}`}
      <svg viewBox="0 0 12 12" className={`size-3 transition-transform ${expanded ? 'rotate-180' : ''}`} fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden="true">
        <path d="M3 4.5 6 7.5 9 4.5" />
      </svg>
    </button>
  )
}

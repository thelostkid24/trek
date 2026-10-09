/**
 * The Empty Valley mark: two peaks with the valley between them, and the sun (the traveller) above.
 * `onLight`: brand green peaks on light surfaces (white in the dark theme); otherwise white, for photos and dark
 * bars. The sun is always sunset red-orange.
 */
export function Logo({ className = '', onLight = false }: { className?: string; onLight?: boolean }) {
  return (
    <svg viewBox="200 38 744 418" className={className} aria-hidden="true">
      <path
        d="M215 440 479 108 645 276 735 193 928 440 730 440 611 320 494 440Z"
        className={
          onLight
            ? 'fill-brand-800 stroke-brand-800 in-data-[theme=dark]:fill-white in-data-[theme=dark]:stroke-white'
            : 'fill-white stroke-white'
        }
        strokeWidth="22"
        strokeLinejoin="round"
      />
      <circle cx="612" cy="100" r="58" className="fill-sunset-500" />
    </svg>
  )
}

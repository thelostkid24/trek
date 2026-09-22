const SIZES = {
  sm: 'size-8 text-xs',
  md: 'size-14 text-lg',
  lg: 'size-24 text-3xl sm:size-28',
}

/** Profile photo, or initials on a brand tint when there is none. */
export function Avatar({
  url,
  name,
  size = 'sm',
  className = '',
}: {
  url: string | null
  /** Used for initials and alt text; falls back to a generic glyph. */
  name: string | null
  size?: keyof typeof SIZES
  className?: string
}) {
  const base = `${SIZES[size]} shrink-0 rounded-full ring-2 ring-white ${className}`
  if (url) {
    return <img src={url} alt={name ? `${name}'s photo` : 'Profile photo'} className={`${base} object-cover`} />
  }
  return (
    <span
      aria-hidden="true"
      className={`${base} inline-flex items-center justify-center bg-brand-100 font-display font-semibold text-brand-800`}
    >
      {initials(name)}
    </span>
  )
}

function initials(name: string | null): string {
  const parts = (name ?? '').trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) return '?'
  const first = parts[0][0]
  const last = parts.length > 1 ? parts[parts.length - 1][0] : ''
  return (first + last).toUpperCase()
}

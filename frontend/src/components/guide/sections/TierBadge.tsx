import { useQuery } from '@tanstack/react-query'
import { credentialsKey, getGuideCredentials, TIER_LABELS } from '../../../api/certifications.ts'
import type { GuideSectionProps } from './types.ts'

/**
 * The tier chip beside the name, e.g. "Summit Guide", worked out by the server on every read (§7.21.3), and
 * "Certifications verified by Empty Valley" once any certificate is verified and unexpired.
 */
export function TierBadge({ guide }: GuideSectionProps) {
  const credentials = useQuery({ queryKey: credentialsKey(guide.id), queryFn: () => getGuideCredentials(guide.id) })
  if (!credentials.data) return null
  const { tier, verified } = credentials.data
  if (!tier && !verified) return null
  return (
    <span className="inline-flex flex-wrap items-center gap-2">
      {tier && (
        <span className="inline-flex items-center gap-1.5 rounded-full bg-brand-900 px-3 py-1 text-xs font-semibold text-white">
          <svg viewBox="0 0 20 20" className="size-3.5 text-brand-200" fill="currentColor" aria-hidden="true">
            <path d="M7.5 4.5a.6.6 0 0 1 1 0l2.7 4.4 1.1-1.6a.6.6 0 0 1 1 0L18 15H2l5.5-10.5Z" />
          </svg>
          {TIER_LABELS[tier]}
        </span>
      )}
      {verified && (
        <span className="inline-flex items-center gap-1.5 rounded-full bg-brand-100 px-3 py-1 text-xs font-semibold text-brand-900">
          <ShieldIcon className="size-3.5 text-brand-700" />
          Certifications verified by Empty Valley
        </span>
      )}
    </span>
  )
}

export function ShieldIcon({ className = '' }: { className?: string }) {
  return (
    <svg viewBox="0 0 20 20" className={className} fill="currentColor" aria-hidden="true">
      <path d="M10 1.5 3.5 4v5c0 4 2.8 7.6 6.5 9 3.7-1.4 6.5-5 6.5-9V4L10 1.5Zm-1 12L5.5 10l1.4-1.4L9 10.7l4.1-4.1 1.4 1.4L9 13.5Z" />
    </svg>
  )
}

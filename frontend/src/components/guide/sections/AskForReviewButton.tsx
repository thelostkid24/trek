import { Link } from 'react-router-dom'
import type { GuideSectionProps } from './types.ts'

/** The owner bar's "Ask for review" (docs/TRD.md §7.21.5): opens Guide → Reviews, where the link and QR live. */
export function AskForReviewButton({ owner }: GuideSectionProps) {
  if (!owner) return null
  return (
    <Link
      to="/guide/reviews"
      className="rounded-full border border-paper-300 bg-white px-4 py-2 text-sm font-medium text-ink-900 hover:border-ink-900"
    >
      Ask for review
    </Link>
  )
}

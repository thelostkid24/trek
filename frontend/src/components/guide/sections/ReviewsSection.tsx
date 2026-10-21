import { ReviewList } from '../../catalog/GuideProfileCard.tsx'
import { SectionLabel } from '../../catalog/TrekSections.tsx'
import type { GuideSectionProps } from './types.ts'

/** What trekkers say (§7.21.5). For now the §7.14 list. */
export function ReviewsSection({ guide: g }: GuideSectionProps) {
  const first = (g.full_name ?? 'Local guide').split(' ')[0]
  return (
    <section>
      <SectionLabel aside={g.review_count > 0 ? `${g.review_count} ${g.review_count === 1 ? 'review' : 'reviews'}` : undefined}>
        What trekkers say
      </SectionLabel>
      {g.reviews.length === 0 ? (
        <p className="mt-3 text-sm text-stone-600">Reviews appear here after {first}'s treks are completed.</p>
      ) : (
        <ReviewList reviews={g.reviews} />
      )}
    </section>
  )
}

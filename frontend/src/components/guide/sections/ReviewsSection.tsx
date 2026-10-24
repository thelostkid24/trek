import { useInfiniteQuery, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import {
  getGuideReviews,
  getReviewsSummary,
  SUB_RATINGS,
  tagLabel,
  type ReviewSource,
  type ReviewsSummary,
} from '../../../api/reviews.ts'
import { SectionLabel } from '../../catalog/TrekSections.tsx'
import { ReviewCard } from '../../review/ReviewCard.tsx'
import { WriteReviewDialog } from '../../review/WriteReviewDialog.tsx'
import type { GuideSectionProps } from './types.ts'

const PAGE = 6

const CATEGORY_LABEL: Record<ReviewsSummary['categories'][number]['key'], string> = {
  OVERALL: 'Overall',
  ...Object.fromEntries(SUB_RATINGS.map((s) => [s.key, s.label])),
} as Record<ReviewsSummary['categories'][number]['key'], string>

/**
 * What trekkers say (docs/TRD.md §7.21.5): the verified rating, a bar per category (five answers or more), tags
 * mentioned often, filters by source, the reviews with the guide's replies, and "Write a review" (a pop-up) for anyone
 * who trekked with them. The owner can reply from here.
 */
export function ReviewsSection({ guide: g, owner }: GuideSectionProps) {
  const key = g.slug ?? g.id
  const first = (g.full_name ?? 'Local guide').split(' ')[0]
  const [source, setSource] = useState<ReviewSource | null>(null)
  const queryClient = useQueryClient()
  const summary = useQuery({ queryKey: ['reviews-summary', key], queryFn: () => getReviewsSummary(key) })
  const list = useInfiniteQuery({
    queryKey: ['guide-reviews', key, source],
    queryFn: ({ pageParam }) => getGuideReviews(key, source, pageParam, PAGE),
    initialPageParam: 0,
    getNextPageParam: (last) => ((last.page + 1) * last.size < last.total ? last.page + 1 : undefined),
  })
  const s = summary.data
  const items = list.data?.pages.flatMap((p) => p.items) ?? []
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['guide-reviews', key] })

  const filters: { value: ReviewSource | null; label: string; count: number }[] = s
    ? [
        { value: null, label: 'All', count: s.counts.all },
        { value: 'BOOKING', label: 'Verified trek', count: s.counts.booking },
        { value: 'OWN_TREKKER', label: 'Group verified', count: s.counts.own_trekker },
      ]
    : []

  return (
    <section>
      <SectionLabel
        aside={
          s && s.average !== null ? `★ ${s.average.toFixed(1)} · ${s.count} ${s.count === 1 ? 'review' : 'reviews'}` : undefined
        }
      >
        Reviews
      </SectionLabel>

      {s && s.categories.length > 0 && (
        <dl className="mt-4 grid gap-2 sm:max-w-md">
          {s.categories.map((c) => (
            <div key={c.key} className="grid grid-cols-[9rem_1fr_2rem] items-center gap-3 text-sm">
              <dt className="text-stone-700">{CATEGORY_LABEL[c.key]}</dt>
              <dd className="h-1.5 overflow-hidden rounded-full bg-paper-200" aria-hidden>
                <div className="h-full rounded-full bg-laterite-600" style={{ width: `${(c.average / 5) * 100}%` }} />
              </dd>
              <dd className="text-right tabular-nums text-stone-900">{c.average.toFixed(1)}</dd>
            </div>
          ))}
        </dl>
      )}

      {s && s.mentioned_often.length > 0 && (
        <div className="mt-4">
          <p className="text-sm font-medium text-stone-800">Mentioned often</p>
          <p className="mt-2 flex flex-wrap gap-2">
            {s.mentioned_often.map((m) => (
              <span key={m.tag} className="rounded-full bg-paper-100 px-3 py-1 text-sm text-stone-700 ring-1 ring-paper-300">
                {tagLabel(m.tag)} <span className="text-stone-500">· {m.count}</span>
              </span>
            ))}
          </p>
        </div>
      )}

      {s && s.counts.all > 0 && (
        <div role="group" aria-label="Filter reviews" className="mt-5 flex flex-wrap gap-2">
          {filters
            .filter((f) => f.value === null || f.count > 0)
            .map((f) => (
              <button
                key={f.label}
                type="button"
                aria-pressed={source === f.value}
                onClick={() => setSource(f.value)}
                className={`rounded-full px-3 py-1.5 text-sm ring-1 ${
                  source === f.value ? 'bg-stone-900 text-white ring-stone-900' : 'bg-white text-stone-700 ring-paper-300'
                }`}
              >
                {f.label} {f.count}
              </button>
            ))}
        </div>
      )}

      {summary.isSuccess && s?.counts.all === 0 ? (
        <p className="mt-3 text-sm text-stone-600">No reviews yet.</p>
      ) : (
        <ul className="mt-4 grid gap-3 sm:grid-cols-2">
          {items.map((r) => (
            <ReviewCard key={r.id} review={r} guideFirst={first} onReplyChanged={owner ? refresh : undefined} />
          ))}
        </ul>
      )}
      {list.hasNextPage && (
        <button
          type="button"
          onClick={() => list.fetchNextPage()}
          disabled={list.isFetchingNextPage}
          className="mt-3 text-sm font-medium text-pine-700 hover:underline"
        >
          {list.isFetchingNextPage ? 'Loading…' : 'Show more reviews'}
        </button>
      )}

      {!owner && (
        <div className="mt-5 flex flex-col gap-3 rounded-xl bg-paper-100 p-4 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-sm text-stone-700">
            <span className="font-medium text-stone-900">Trekked with {first}?</span> Anyone who trekked with {first} can
            leave a review here.
          </p>
          <WriteReviewDialog guideKey={key} guideName={g.full_name ?? 'your guide'} />
        </div>
      )}
    </section>
  )
}

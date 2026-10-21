import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import {
  dismissReports,
  getAdminReviews,
  hideReview,
  unhideReview,
  type AdminReviewItem,
  type AdminReviewView,
} from '../../api/reviews.ts'
import { useAuth } from '../../auth/useAuth.ts'
import { Button, ErrorNote, Loading, Panel } from '../../components/admin/AdminUi.tsx'
import { ReviewCard } from '../../components/review/ReviewCard.tsx'

const VIEWS: { value: AdminReviewView; label: string }[] = [
  { value: 'REPORTED', label: 'Reported' },
  { value: 'RECENT', label: 'Recent' },
  { value: 'HIDDEN', label: 'Hidden' },
]

/**
 * /admin/reviews (docs/TRD.md §7.21.5): reviews and guide replies go live at once; here an admin hides or unhides
 * them with a reason (audit-logged) and closes guides' reports.
 */
export function ReviewsAdminPage() {
  const { withAuth } = useAuth()
  const [view, setView] = useState<AdminReviewView>('REPORTED')
  const reviews = useQuery({
    queryKey: ['admin-reviews', view],
    queryFn: () => withAuth((token) => getAdminReviews(token, view)),
  })

  return (
    <Panel
      title="Reviews"
      action={
        <div role="group" aria-label="Show" className="flex gap-1">
          {VIEWS.map((v) => (
            <Button key={v.value} tone={view === v.value ? 'primary' : 'secondary'} aria-pressed={view === v.value}
              onClick={() => setView(v.value)}>
              {v.label}
            </Button>
          ))}
        </div>
      }
    >
      {reviews.isPending ? (
        <Loading />
      ) : reviews.isError ? (
        <ErrorNote error={reviews.error} />
      ) : reviews.data.items.length === 0 ? (
        <p className="text-sm text-stone-600">Nothing here.</p>
      ) : (
        <ul className="grid gap-3">
          {reviews.data.items.map((item) => (
            <ReviewCard
              key={item.review.id}
              review={item.review}
              guideFirst={(item.guide_name ?? 'Guide').split(' ')[0]}
              footer={<Moderation item={item} />}
            />
          ))}
        </ul>
      )}
    </Panel>
  )
}

function Moderation({ item }: { item: AdminReviewItem }) {
  const { withAuth } = useAuth()
  const queryClient = useQueryClient()
  const [hiding, setHiding] = useState<'review' | 'reply' | null>(null)
  const [reason, setReason] = useState('')
  const done = () => {
    setHiding(null)
    setReason('')
    return queryClient.invalidateQueries({ queryKey: ['admin-reviews'] })
  }
  const act = useMutation({
    mutationFn: (a: { kind: 'hide' | 'unhide' | 'dismiss'; reply: boolean }) =>
      withAuth((token) =>
        a.kind === 'hide'
          ? hideReview(token, item.review.id, a.reply, reason.trim())
          : a.kind === 'unhide'
            ? unhideReview(token, item.review.id, a.reply)
            : dismissReports(token, item.review.id),
      ),
    onSuccess: done,
  })
  const hasReply = item.review.reply !== null

  return (
    <div className="mt-3 space-y-2 border-t border-stone-200 pt-3 text-sm">
      <p className="text-stone-600">
        About{' '}
        {item.guide_slug ? (
          <Link to={`/guides/${item.guide_slug}`} className="font-medium text-brand-800 hover:underline">{item.guide_name}</Link>
        ) : (
          item.guide_name
        )}
        {item.reviewer_contact && <> · from {item.reviewer_contact}</>}
      </p>
      {item.reports.map((r, i) => (
        <p key={i} className="rounded-lg bg-amber-100 px-3 py-2 text-amber-800">
          Reported by {r.reporter_name ?? 'the guide'}: {r.reason}
        </p>
      ))}
      {item.hidden_reason && <p className="text-laterite-600">Review hidden: {item.hidden_reason}</p>}
      {item.reply_hidden_reason && <p className="text-laterite-600">Reply hidden: {item.reply_hidden_reason}</p>}
      <ErrorNote error={act.error} />
      {hiding ? (
        <form className="space-y-2" onSubmit={(e) => { e.preventDefault(); if (reason.trim()) act.mutate({ kind: 'hide', reply: hiding === 'reply' }) }}>
          <label htmlFor={`hide-${item.review.id}`} className="block text-stone-700">
            Why hide this {hiding}? (kept on record)
          </label>
          <input id={`hide-${item.review.id}`} value={reason} maxLength={500} onChange={(e) => setReason(e.target.value)}
            className="w-full rounded-lg border border-stone-300 px-3 py-2" />
          <div className="flex gap-2">
            <Button type="submit" tone="danger" disabled={!reason.trim() || act.isPending}>Hide</Button>
            <Button tone="secondary" onClick={() => setHiding(null)}>Cancel</Button>
          </div>
        </form>
      ) : (
        <div className="flex flex-wrap gap-2">
          {item.hidden_at ? (
            <Button tone="secondary" disabled={act.isPending} onClick={() => act.mutate({ kind: 'unhide', reply: false })}>Unhide review</Button>
          ) : (
            <Button tone="danger" onClick={() => setHiding('review')}>Hide review</Button>
          )}
          {hasReply && (item.reply_hidden_at ? (
            <Button tone="secondary" disabled={act.isPending} onClick={() => act.mutate({ kind: 'unhide', reply: true })}>Unhide reply</Button>
          ) : (
            <Button tone="secondary" onClick={() => setHiding('reply')}>Hide reply</Button>
          ))}
          {item.reports.length > 0 && (
            <Button tone="secondary" disabled={act.isPending} onClick={() => act.mutate({ kind: 'dismiss', reply: false })}>Dismiss reports</Button>
          )}
        </div>
      )}
    </div>
  )
}

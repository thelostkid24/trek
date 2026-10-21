import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { getReview, writeReview, type Booking } from '../../api/bookings.ts'
import { ApiError } from '../../api/client.ts'
import { fieldErrors, messageFor } from '../../auth/errorMessages.ts'
import { useAuth } from '../../auth/useAuth.ts'
import type { ReviewTag, SubRatings } from '../../api/reviews.ts'
import { TextAreaField } from '../profile/fields.tsx'
import { StarPicker, SubRatingFields, TagChips } from '../review/ReviewFields.tsx'

/**
 * "How was it?" on a completed trek (docs/TRD.md §7.14, §7.21.5): stars, optional sub-ratings and tags, and a few
 * words about the guide. It shows on the
 * guide's page with the trekker's first name; they can change it later.
 */
export function ReviewSection({ booking: b }: { booking: Booking }) {
  const { withAuth } = useAuth()
  const queryKey = ['review', b.id]
  const review = useQuery({
    queryKey,
    queryFn: () =>
      withAuth((token) => getReview(token, b.id)).catch((e: unknown) => {
        if (e instanceof ApiError && e.status === 404) return null
        throw e
      }),
  })
  if (review.isPending) return null
  const guide = b.departure.guide.full_name?.split(' ')[0] ?? 'your guide'

  return (
    <section className="rounded-(--card-radius) border border-paper-300 bg-paper-50 p-5 sm:p-7">
      <h2 className="font-display text-xl font-medium text-stone-900">How was it with {guide}?</h2>
      {review.isError ? (
        <p className="mt-2 text-sm text-laterite-600">{messageFor(review.error)}</p>
      ) : (
        <ReviewForm
          key={review.data?.updated_at ?? 'new'}
          bookingId={b.id}
          initial={review.data ?? null}
          queryKey={queryKey}
        />
      )}
    </section>
  )
}

function ReviewForm({
  bookingId,
  initial,
  queryKey,
}: {
  bookingId: string
  initial: (SubRatings & { rating: number; body: string | null; tags: ReviewTag[] }) | null
  queryKey: string[]
}) {
  const { withAuth } = useAuth()
  const queryClient = useQueryClient()
  const [rating, setRating] = useState(initial?.rating ?? 0)
  const [body, setBody] = useState(initial?.body ?? '')
  const [subs, setSubs] = useState<SubRatings>({
    safety_rating: initial?.safety_rating ?? null,
    route_rating: initial?.route_rating ?? null,
    communication_rating: initial?.communication_rating ?? null,
    care_rating: initial?.care_rating ?? null,
  })
  const [tags, setTags] = useState<ReviewTag[]>(initial?.tags ?? [])
  const save = useMutation({
    mutationFn: () =>
      withAuth((token) => writeReview(token, bookingId, { rating, body: body.trim() || null, tags, ...subs })),
    onSuccess: (data) => queryClient.setQueryData(queryKey, data),
  })
  const errors = fieldErrors(save.error)
  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (rating > 0) save.mutate()
  }

  return (
    <form onSubmit={submit} className="mt-3 space-y-3">
      <StarPicker label="Rating" value={rating} onChange={setRating} />
      <SubRatingFields value={subs} onChange={setSubs} />
      <TagChips value={tags} onChange={setTags} />
      <TextAreaField label="A few words (optional)" name="review_body" maxLength={2000} rows={3} value={body}
        onChange={(e) => setBody(e.target.value)} error={errors.body}
        hint="Shown on your guide's page with your first name." />
      {save.error && Object.keys(errors).length === 0 && <p className="text-sm text-laterite-600">{messageFor(save.error)}</p>}
      <div className="flex items-center gap-3">
        <button
          type="submit"
          disabled={rating === 0 || save.isPending}
          className="rounded-full bg-pine-700 px-4 py-2 text-sm font-medium text-white hover:bg-pine-600 disabled:opacity-50"
        >
          {save.isPending ? 'Saving…' : initial ? 'Update review' : 'Post review'}
        </button>
        {initial && !save.isPending && <span className="text-sm text-stone-500">Thanks — your review is up.</span>}
      </div>
    </form>
  )
}

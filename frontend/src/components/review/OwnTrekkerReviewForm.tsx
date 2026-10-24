import { useMutation } from '@tanstack/react-query'
import { useState, type FormEvent, type ReactNode } from 'react'
import { postOwnTrekkerReview, type ReviewTag, type SubRatings } from '../../api/reviews.ts'
import { fieldErrors, messageFor } from '../../auth/errorMessages.ts'
import { TextField } from '../auth/TextField.tsx'
import { TextAreaField } from '../profile/fields.tsx'
import { StarPicker, SubRatingFields, TagChips } from './ReviewFields.tsx'

/**
 * A review from someone who trekked with the guide off-platform (docs/TRD.md §7.21.5): stars, optional sub-ratings,
 * "What stood out?" with "Other", a few words, their name and trek. No email or phone; it shows once an admin approves
 * it. Used on /r/{slug} and in the guide page's "Write a review" pop-up.
 */
export function OwnTrekkerReviewForm({
  guideKey,
  guideName,
  initialTrek = '',
  after,
}: {
  guideKey: string
  guideName: string
  initialTrek?: string
  /** Shown under the thank-you, e.g. a link back to the profile or a Close button. */
  after?: ReactNode
}) {
  const [rating, setRating] = useState(0)
  const [subs, setSubs] = useState<SubRatings>({})
  const [tags, setTags] = useState<ReviewTag[]>([])
  const [other, setOther] = useState<string | null>(null)
  const [body, setBody] = useState('')
  const [name, setName] = useState('')
  const [trek, setTrek] = useState(initialTrek)

  const post = useMutation({
    mutationFn: () =>
      postOwnTrekkerReview(guideKey, {
        rating,
        ...subs,
        tags,
        other_tag: other?.trim() || null,
        body: body.trim() || null,
        name: name.trim(),
        trek_name: trek.trim() || null,
      }),
  })
  const errors = fieldErrors(post.error)
  const first = guideName.split(' ')[0]

  if (post.isSuccess) {
    return (
      <div className="rounded-(--card-radius) bg-paper-100 p-6 text-center">
        <p className="font-display text-xl text-stone-900">Thank you!</p>
        <p className="mt-2 text-sm text-stone-700">Your review will show on {first}'s profile once we've checked it.</p>
        {after && <div className="mt-4">{after}</div>}
      </div>
    )
  }

  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (rating > 0 && name.trim()) post.mutate()
  }

  return (
    <form onSubmit={submit} className="space-y-6">
      <div className="flex flex-col items-center gap-1">
        <StarPicker label="Rating" value={rating} onChange={setRating} />
        <p className="text-xs text-stone-500">{rating === 0 ? 'Tap a star' : `${rating} out of 5`}</p>
      </div>
      <SubRatingFields value={subs} onChange={setSubs} />
      <TagChips value={tags} onChange={setTags} other={{ value: other, onChange: setOther, error: errors.other_tag }} />
      <TextAreaField label="A few words (optional)" name="body" maxLength={2000} rows={3} value={body}
        onChange={(e) => setBody(e.target.value)} error={errors.body} />
      <TextField label="Your name" name="name" maxLength={60} value={name} autoComplete="given-name"
        onChange={(e) => setName(e.target.value)} error={errors.name} hint="Only your first name is shown." />
      <TextField label="Which trek? (optional)" name="trek_name" maxLength={100} value={trek}
        onChange={(e) => setTrek(e.target.value)} error={errors.trek_name} />
      {post.error && Object.keys(errors).length === 0 && (
        <p role="alert" className="text-sm text-laterite-600">{messageFor(post.error)}</p>
      )}
      <button
        type="submit"
        disabled={rating === 0 || !name.trim() || post.isPending}
        className="w-full rounded-full bg-pine-700 px-4 py-3 text-sm font-medium text-white hover:bg-pine-600 disabled:opacity-50"
      >
        {post.isPending ? 'Submitting…' : 'Submit'}
      </button>
    </form>
  )
}

import { useMutation, useQuery } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, Navigate, useParams, useSearchParams } from 'react-router-dom'
import { getGuide, guidePath } from '../api/catalog.ts'
import { ApiError } from '../api/client.ts'
import {
  getReviewableBooking,
  postOwnTrekkerReview,
  sendReviewCode,
  type ReviewTag,
  type SubRatings,
} from '../api/reviews.ts'
import { fieldErrors, messageFor } from '../auth/errorMessages.ts'
import { PHONE_OTP_ENABLED } from '../auth/phoneOtp.ts'
import { useAuth } from '../auth/useAuth.ts'
import { useCountdown } from '../auth/useCountdown.ts'
import { TextField } from '../components/auth/TextField.tsx'
import { Avatar } from '../components/Avatar.tsx'
import { TextAreaField } from '../components/profile/fields.tsx'
import { StarPicker, SubRatingFields, TagChips } from '../components/review/ReviewFields.tsx'
import { Seo } from '../components/Seo.tsx'

/**
 * /r/{slug} — "How was your trek?" (docs/TRD.md §7.21.5). The link a guide shares with anyone they trekked with.
 * A signed-in trekker with a completed booking with this guide goes to that booking's review instead; everyone else
 * confirms an email (or a phone, with VITE_PHONE_OTP) with a one-time code. Posting again from the same address
 * rewrites the review.
 */
export function ReviewLinkPage() {
  const { slug = '' } = useParams()
  const [params] = useSearchParams()
  const auth = useAuth()
  const guide = useQuery({ queryKey: ['guide', slug], queryFn: () => getGuide(slug) })
  const isTrekker = auth.status === 'authenticated' && auth.user.role === 'TREKKER'
  const booking = useQuery({
    queryKey: ['review-booking', slug],
    enabled: isTrekker,
    retry: false,
    queryFn: () =>
      auth.withAuth((token) => getReviewableBooking(token, slug)).catch((e: unknown) => {
        if (e instanceof ApiError && e.status === 404) return null
        throw e
      }),
  })

  if (booking.data) return <Navigate to={`/account/bookings/${booking.data.booking_id}`} replace />

  return (
    <div className="mx-auto max-w-md px-4 py-8 sm:py-12">
      <Seo title="How was your trek?" noindex />
      {guide.isError ? (
        <p className="text-stone-700">{messageFor(guide.error)}</p>
      ) : !guide.data || auth.status === 'loading' || (isTrekker && booking.isPending) ? (
        <div className="h-64 animate-pulse rounded-(--card-radius) bg-paper-200" aria-busy="true" aria-label="Loading" />
      ) : (
        <>
          <header className="flex flex-col items-center text-center">
            <Avatar url={guide.data.avatar_url} name={guide.data.full_name} size="md" />
            <p className="mt-3 text-sm text-stone-500">{guide.data.full_name}</p>
            <h1 className="mt-1 font-display text-3xl font-light text-stone-900">How was your trek?</h1>
          </header>
          <ReviewForm
            guideKey={guide.data.slug ?? guide.data.id}
            guideName={guide.data.full_name ?? 'your guide'}
            profilePath={guidePath(guide.data)}
            initialTrek={params.get('trek') ?? ''}
          />
        </>
      )}
    </div>
  )
}

function ReviewForm({
  guideKey,
  guideName,
  profilePath,
  initialTrek,
}: {
  guideKey: string
  guideName: string
  profilePath: string
  initialTrek: string
}) {
  const [rating, setRating] = useState(0)
  const [subs, setSubs] = useState<SubRatings>({})
  const [tags, setTags] = useState<ReviewTag[]>([])
  const [body, setBody] = useState('')
  const [name, setName] = useState('')
  const [trek, setTrek] = useState(initialTrek)
  const [channel, setChannel] = useState<'EMAIL' | 'PHONE'>('EMAIL')
  const [email, setEmail] = useState('')
  const [phone, setPhone] = useState('')
  const [code, setCode] = useState('')
  const [cooldown, startCooldown] = useCountdown()
  const contact = channel === 'EMAIL' ? { email: email.trim() } : { phone: `+91${phone.replace(/\D/g, '').slice(-10)}` }

  const send = useMutation({
    mutationFn: () => sendReviewCode(guideKey, { channel, ...contact }),
    onSuccess: (r) => startCooldown(r.resend_after),
  })
  const post = useMutation({
    mutationFn: () =>
      postOwnTrekkerReview(guideKey, {
        rating,
        ...subs,
        tags,
        body: body.trim() || null,
        name: name.trim(),
        trek_name: trek.trim() || null,
        ...contact,
        code: code.trim(),
      }),
  })
  const errors = { ...fieldErrors(send.error), ...fieldErrors(post.error) }
  const first = guideName.split(' ')[0]

  if (post.isSuccess) {
    return (
      <div className="mt-8 rounded-(--card-radius) bg-paper-100 p-6 text-center">
        <p className="font-display text-xl text-stone-900">Thank you!</p>
        <p className="mt-2 text-sm text-stone-700">
          {post.data.created ? 'Your review is up' : 'Your review is updated'} on {first}'s profile.
        </p>
        <Link to={profilePath} className="mt-4 inline-block text-sm font-medium text-pine-700 hover:underline">
          See {first}'s profile →
        </Link>
      </div>
    )
  }

  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (rating > 0 && name.trim() && code.trim()) post.mutate()
  }

  return (
    <form onSubmit={submit} className="mt-8 space-y-6">
      <div className="flex flex-col items-center gap-1">
        <StarPicker label="Rating" value={rating} onChange={setRating} />
        <p className="text-xs text-stone-500">{rating === 0 ? 'Tap a star' : `${rating} out of 5`}</p>
      </div>
      <SubRatingFields value={subs} onChange={setSubs} />
      <TagChips value={tags} onChange={setTags} />
      <TextAreaField label="A few words (optional)" name="body" maxLength={2000} rows={3} value={body}
        onChange={(e) => setBody(e.target.value)} error={errors.body} />
      <TextField label="Your name" name="name" maxLength={60} value={name} autoComplete="given-name"
        onChange={(e) => setName(e.target.value)} error={errors.name} hint="Only your first name is shown." />
      <TextField label="Which trek? (optional)" name="trek_name" maxLength={100} value={trek}
        onChange={(e) => setTrek(e.target.value)} error={errors.trek_name} />

      {PHONE_OTP_ENABLED && (
        <div role="radiogroup" aria-label="Confirm with" className="flex gap-2">
          {(['EMAIL', 'PHONE'] as const).map((c) => (
            <button key={c} type="button" role="radio" aria-checked={channel === c}
              onClick={() => { setChannel(c); setCode('') }}
              className={`rounded-full px-3 py-1.5 text-sm ring-1 ${channel === c ? 'bg-stone-900 text-white ring-stone-900' : 'bg-white text-stone-700 ring-paper-300'}`}>
              {c === 'EMAIL' ? 'Email' : 'Phone'}
            </button>
          ))}
        </div>
      )}
      <div className="flex items-end gap-2">
        <div className="flex-1">
          {channel === 'EMAIL' ? (
            <TextField label="Email" name="email" type="email" autoComplete="email" value={email}
              onChange={(e) => setEmail(e.target.value)} error={errors.email} />
          ) : (
            <TextField label="Mobile number" name="phone" type="tel" prefix="+91" autoComplete="tel-national"
              inputMode="numeric" value={phone} onChange={(e) => setPhone(e.target.value)} error={errors.phone} />
          )}
        </div>
        <button
          type="button"
          disabled={send.isPending || cooldown > 0 || (channel === 'EMAIL' ? !email.includes('@') : phone.replace(/\D/g, '').length < 10)}
          onClick={() => send.mutate()}
          className="mb-0.5 rounded-full border border-paper-300 bg-white px-4 py-2.5 text-sm font-medium text-stone-900 disabled:opacity-50"
        >
          {cooldown > 0 ? `Resend in ${cooldown}s` : send.isSuccess ? 'Resend code' : 'Send code'}
        </button>
      </div>
      {send.isSuccess && (
        <TextField label="Code" name="code" inputMode="numeric" autoComplete="one-time-code" maxLength={6} value={code}
          onChange={(e) => setCode(e.target.value)} error={errors.code}
          hint={`We sent a 6-digit code to your ${channel === 'EMAIL' ? 'email' : 'phone'}.`} />
      )}
      {[send.error, post.error].map(
        (err, i) =>
          err && Object.keys(fieldErrors(err)).length === 0 && (
            <p key={i} role="alert" className="text-sm text-laterite-600">{messageFor(err)}</p>
          ),
      )}
      <button
        type="submit"
        disabled={rating === 0 || !name.trim() || code.trim().length !== 6 || post.isPending}
        className="w-full rounded-full bg-pine-700 px-4 py-3 text-sm font-medium text-white hover:bg-pine-600 disabled:opacity-50"
      >
        {post.isPending ? 'Posting…' : 'Post review'}
      </button>
      <p className="text-center text-xs text-stone-500">
        No app, no sign-up. We send you a code once to confirm it's really you.
      </p>
    </form>
  )
}

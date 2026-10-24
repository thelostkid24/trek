import { useQuery } from '@tanstack/react-query'
import { Link, Navigate, useParams, useSearchParams } from 'react-router-dom'
import { getGuide, guidePath } from '../api/catalog.ts'
import { ApiError } from '../api/client.ts'
import { getReviewableBooking } from '../api/reviews.ts'
import { messageFor } from '../auth/errorMessages.ts'
import { useAuth } from '../auth/useAuth.ts'
import { Avatar } from '../components/Avatar.tsx'
import { OwnTrekkerReviewForm } from '../components/review/OwnTrekkerReviewForm.tsx'
import { Seo } from '../components/Seo.tsx'

/**
 * /r/{slug} — "How was your trek?" (docs/TRD.md §7.21.5). The link a guide shares with anyone they trekked with.
 * A signed-in trekker with a completed booking with this guide goes to that booking's review instead; anyone else
 * fills in `OwnTrekkerReviewForm`, with no email or phone, and the review shows once an admin approves it.
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
          <div className="mt-8">
            <OwnTrekkerReviewForm
              guideKey={guide.data.slug ?? guide.data.id}
              guideName={guide.data.full_name ?? 'your guide'}
              initialTrek={params.get('trek') ?? ''}
              after={
                <Link to={guidePath(guide.data)} className="text-sm font-medium text-pine-700 hover:underline">
                  See {(guide.data.full_name ?? 'your guide').split(' ')[0]}'s profile →
                </Link>
              }
            />
          </div>
        </>
      )}
    </div>
  )
}

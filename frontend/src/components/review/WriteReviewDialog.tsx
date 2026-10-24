import { useQuery } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError } from '../../api/client.ts'
import { getReviewableBooking } from '../../api/reviews.ts'
import { useAuth } from '../../auth/useAuth.ts'
import { OwnTrekkerReviewForm } from './OwnTrekkerReviewForm.tsx'

/**
 * "Write a review" on a guide's page: opens the review form in a pop-up (docs/TRD.md §7.21.5). A signed-in trekker
 * with a completed booking with this guide is pointed to that booking's review instead, which counts as "Verified trek".
 */
export function WriteReviewDialog({ guideKey, guideName }: { guideKey: string; guideName: string }) {
  const [open, setOpen] = useState(false)
  // A new form each time it opens, so a sent review doesn't stay on screen.
  const [round, setRound] = useState(0)
  const dialog = useRef<HTMLDialogElement>(null)
  const first = guideName.split(' ')[0]

  useEffect(() => {
    const d = dialog.current
    if (!d) return
    if (open && !d.open) d.showModal()
    if (!open && d.open) d.close()
  }, [open])

  return (
    <>
      <button
        type="button"
        onClick={() => {
          setRound((n) => n + 1)
          setOpen(true)
        }}
        className="shrink-0 rounded-full bg-stone-900 px-4 py-2 text-center text-sm font-medium text-white hover:bg-stone-800"
      >
        Write a review
      </button>
      <dialog
        ref={dialog}
        aria-labelledby="write-review-title"
        onClose={() => setOpen(false)}
        className="m-auto max-h-[90dvh] w-[min(32rem,calc(100vw-2rem))] rounded-(--card-radius) bg-white p-0 text-stone-900 shadow-xl backdrop:bg-stone-950/50"
      >
        {open && (
          <div className="p-5 sm:p-6">
            <div className="flex items-start justify-between gap-3">
              <div>
                <p className="text-sm text-stone-500">{guideName}</p>
                <h2 id="write-review-title" className="font-display text-2xl font-light text-stone-900">
                  How was your trek?
                </h2>
              </div>
              <button
                type="button"
                onClick={() => setOpen(false)}
                aria-label="Close"
                className="-mt-1 -mr-1 rounded-full p-2 text-stone-500 hover:bg-paper-100 hover:text-stone-900"
              >
                <svg viewBox="0 0 24 24" className="size-5" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
                  <path d="M6 6l12 12M18 6 6 18" />
                </svg>
              </button>
            </div>
            <div className="mt-5">
              <DialogBody key={round} guideKey={guideKey} guideName={guideName} first={first} onClose={() => setOpen(false)} />
            </div>
          </div>
        )}
      </dialog>
    </>
  )
}

function DialogBody({ guideKey, guideName, first, onClose }: { guideKey: string; guideName: string; first: string; onClose: () => void }) {
  const auth = useAuth()
  const isTrekker = auth.status === 'authenticated' && auth.user.role === 'TREKKER'
  const booking = useQuery({
    queryKey: ['review-booking', guideKey],
    enabled: isTrekker,
    retry: false,
    queryFn: () =>
      auth.withAuth((token) => getReviewableBooking(token, guideKey)).catch((e: unknown) => {
        if (e instanceof ApiError && e.status === 404) return null
        throw e
      }),
  })

  if (isTrekker && booking.isPending) {
    return <div className="h-48 animate-pulse rounded-xl bg-paper-100" aria-busy="true" aria-label="Loading" />
  }
  if (booking.data) {
    return (
      <div className="rounded-xl bg-paper-100 p-5 text-center">
        <p className="text-sm text-stone-700">You trekked with {first} through us, so your review counts as a verified trek.</p>
        <Link
          to={`/account/bookings/${booking.data.booking_id}`}
          onClick={onClose}
          className="mt-4 inline-block rounded-full bg-pine-700 px-4 py-2.5 text-sm font-medium text-white hover:bg-pine-600"
        >
          {booking.data.reviewed ? 'Edit your review' : 'Review your trek'}
        </Link>
      </div>
    )
  }
  return (
    <OwnTrekkerReviewForm
      guideKey={guideKey}
      guideName={guideName}
      after={
        <button type="button" onClick={onClose} className="text-sm font-medium text-pine-700 hover:underline">
          Close
        </button>
      }
    />
  )
}

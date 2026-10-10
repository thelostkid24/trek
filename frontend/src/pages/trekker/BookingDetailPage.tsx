import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { CANCEL_REASON_LABEL } from '../../api/admin.ts'
import {
  cancelBooking,
  getBooking,
  getCancellationQuote,
  releaseHold,
  updateTravellers,
  type Booking,
  type Refund,
} from '../../api/bookings.ts'
import { getDeparture } from '../../api/catalog.ts'
import { ApiError } from '../../api/client.ts'
import { describeMethod } from '../../api/payments.ts'
import { fieldErrors, messageFor } from '../../auth/errorMessages.ts'
import { useAuth } from '../../auth/useAuth.ts'
import { Avatar } from '../../components/Avatar.tsx'
import { GuestNotice } from '../../components/booking/GuestNotice.tsx'
import { BookingStatusBadge } from '../../components/booking/BookingStatusBadge.tsx'
import { MoreMenu } from '../../components/booking/MoreMenu.tsx'
import { ReviewSection } from '../../components/booking/ReviewSection.tsx'
import { TravellerFields } from '../../components/booking/TravellerFields.tsx'
import { usePayForBooking, type PayOutcome } from '../../components/booking/usePayForBooking.ts'
import { clock, useSecondsUntil } from '../../components/booking/useSecondsUntil.ts'
import { addonsOffered, type OfferedAddon } from '../../lib/addons.ts'
import { addonLines, blankTraveller, toInput, type TravellerDraft } from '../../lib/travellers.ts'
import { dateRange, dateTime, longDate, rupees, todayIst } from '../../lib/format.ts'

/** /account/bookings/:id — rendered inside <RequireAuth role="TREKKER">. */
export function BookingDetailPage() {
  const { id = '' } = useParams()
  const { withAuth } = useAuth()
  const booking = useQuery({
    queryKey: ['booking', id],
    queryFn: () => withAuth((token) => getBooking(token, id)),
    retry: (count, error) => !(error instanceof ApiError && error.status === 404) && count < 2,
    // A held booking can be confirmed by a webhook or expired by the reconciler at any moment;
    // refunds settle in the background too.
    refetchInterval: (query) => {
      const b = query.state.data
      if (!b) return false
      if (b.status === 'HELD') return 5000
      return b.refunds.some((r) => r.status === 'PENDING') ? 30_000 : false
    },
  })

  if (booking.isPending) {
    return (
      <div className="max-w-3xl space-y-4 px-5 py-8 sm:px-10 sm:py-10" aria-busy="true" aria-label="Loading booking">
        <div className="h-40 animate-pulse rounded-(--card-radius) bg-paper-200" />
        <div className="h-56 animate-pulse rounded-(--card-radius) bg-paper-200/70" />
      </div>
    )
  }
  if (booking.isError) {
    return (
      <section className="mx-auto max-w-md px-4 py-16 text-center">
        <h1 className="font-display text-2xl font-medium text-stone-900">Booking not available</h1>
        <p className="mt-2 text-stone-600">{messageFor(booking.error)}</p>
        <Link to="/account/bookings" className="mt-4 inline-block text-pine-700 underline">
          My treks
        </Link>
      </section>
    )
  }
  return <Detail booking={booking.data} />
}

function Detail({ booking: b }: { booking: Booking }) {
  const d = b.departure
  const auth = useAuth()
  const guest = auth.status === 'authenticated' && auth.user.guest
  const live = b.status === 'HELD' || b.status === 'CONFIRMED'
  // A trek you've walked reads in the past tense.
  const done = b.status === 'CONFIRMED' && d.status === 'COMPLETED'
  // Add-on prices come from the trek; they're needed to fill in who's coming.
  const departure = useQuery({ queryKey: ['public-departure', d.id], queryFn: () => getDeparture(d.id), enabled: live })
  const offered = departure.data ? addonsOffered(departure.data.track) : null
  // Held with add-ons on offer: who's coming (with insurance) comes before paying, beside a live price.
  const checkout = b.status === 'HELD' && offered !== null && offered.length > 0
  // Cancelling is tucked behind "⋯" (here or on My treks, which links with ?cancel=1): an extra, deliberate step.
  const location = useLocation()
  const [showCancel, setShowCancel] = useState(() => new URLSearchParams(location.search).has('cancel'))
  const scrollToCancel = () =>
    requestAnimationFrame(() => document.getElementById('cancel')?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
  const openCancel = () => {
    setShowCancel(true)
    scrollToCancel()
  }
  // Arrived from My treks' "Request cancellation": bring the section into view once.
  const arrivedToCancel = useRef(showCancel)
  useEffect(() => {
    if (arrivedToCancel.current) scrollToCancel()
  }, [])
  const content = (
    <>
      <Link to="/account/bookings" className="text-sm text-pine-700 hover:text-pine-600">
        ← My treks
      </Link>

      <section className="rounded-(--card-radius) border border-paper-300 bg-paper-50 p-5 sm:p-7">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h1 className="font-display text-3xl font-light tracking-[-0.02em] text-stone-900">
              <Link to={`/departures/${d.id}`} className="hover:text-pine-700">
                {d.track.name}
              </Link>
            </h1>
            <p className="mt-1 text-stone-600">{d.start_date === d.end_date ? longDate(d.start_date) : dateRange(d.start_date, d.end_date)}</p>
          </div>
          <div className="flex items-center gap-2">
            <BookingStatusBadge status={b.status} />
            {b.status === 'CONFIRMED' && (
              <MoreMenu label="More options for this booking" items={[{ label: 'Cancel booking', onSelect: openCancel }]} />
            )}
          </div>
        </div>
        <dl className="mt-5 grid gap-4 text-sm sm:grid-cols-3">
          <Fact label="Seats">{b.seats}</Fact>
          <Fact label="Total">
            {rupees(b.amount_paise)}
            {b.addons.total_paise > 0 && (
              <span className="mt-0.5 block text-xs text-stone-500">
                {[
                  b.addons.insurance_seats > 0 && `insurance × ${b.addons.insurance_seats}`,
                  b.addons.offloading_seats > 0 && `offloading × ${b.addons.offloading_seats}`,
                  b.addons.transport_seats > 0 && `transport × ${b.addons.transport_seats}`,
                ]
                  .filter(Boolean)
                  .join(' · ')}{' '}
                included
              </span>
            )}
          </Fact>
          <Fact label="Meeting point">{d.meeting_point}</Fact>
        </dl>
        {d.guide.full_name && (
          <div className="mt-5 flex items-center gap-3 border-t border-paper-300 pt-4">
            <Avatar url={d.guide.avatar_url} name={d.guide.full_name} />
            <p className="text-sm text-stone-700">
              Your guide {done ? 'was' : 'is'}{' '}
              <Link to={`/guides/${d.guide.id}`} className="font-semibold hover:text-pine-700 hover:underline">
                {d.guide.full_name}
              </Link>
            </p>
          </div>
        )}
      </section>

      {b.status === 'CANCELLED_FORCE_MAJEURE' && (
        <Banner tone="warn" title="This departure was cancelled">
          {d.cancel_reason_code && `${CANCEL_REASON_LABEL[d.cancel_reason_code]}: `}
          {d.cancel_reason_note} You get a full refund.
        </Banner>
      )}
      {b.status === 'HELD' && !checkout && <PayPanel booking={b} />}
      {(b.status === 'EXPIRED' || b.status === 'RELEASED') && (
        <Banner tone="muted" title={b.status === 'EXPIRED' ? 'The seat hold ended' : 'You released these seats'}>
          Nothing was charged for this booking unless a refund is listed below.{' '}
          <Link to={`/departures/${d.id}`} className="font-semibold underline">
            Book again
          </Link>
        </Banner>
      )}
      {done && (
        <Banner tone="good" title="You went!">
          Your seats were confirmed and your guide had everyone’s details. We hope the trail treated you well.
        </Banner>
      )}
      {b.status === 'CONFIRMED' && !done && (
        <Banner tone="good" title="You're going!">
          Your seats are confirmed.{' '}
          {b.travellers_complete ? 'Your guide has everyone’s details.' : 'Next, tell your guide who’s coming.'}
        </Banner>
      )}
      {guest && live && <GuestNotice />}

      {checkout ? null : b.status === 'CONFIRMED' || b.travellers.length > 0 ? (
        <TravellersSection key={b.id} booking={b} offered={offered ?? []} />
      ) : (
        live && (
          <p className="rounded-(--card-radius) border border-paper-300 bg-paper-100 px-5 py-4 text-sm text-stone-600">
            Traveller names, medical and dietary details come after payment.
          </p>
        )
      )}

      {!checkout && (b.contact.phone || b.contact.email) && (
        <section className="rounded-(--card-radius) border border-paper-300 bg-paper-50 p-5 sm:p-7">
          <h2 className="font-display text-xl font-medium text-stone-900">Contact</h2>
          <dl className="mt-3 grid gap-4 text-sm sm:grid-cols-3">
            {b.contact.full_name && <Fact label="Name">{b.contact.full_name}</Fact>}
            {b.contact.phone && <Fact label="WhatsApp">{b.contact.phone}</Fact>}
            {b.contact.email && <Fact label="Email">{b.contact.email}</Fact>}
          </dl>
        </section>
      )}

      {done && <ReviewSection booking={b} />}
      {(b.payment?.status === 'PAID' || b.refunds.length > 0) && <PaymentSection booking={b} />}
      {b.status === 'CONFIRMED' && showCancel && <CancelSection booking={b} onKeep={() => setShowCancel(false)} />}
    </>
  )
  if (b.status === 'HELD' && departure.isPending) {
    return <div className="max-w-3xl space-y-5 px-5 py-8 sm:px-10 sm:py-10">{content}</div>
  }
  if (checkout && offered) {
    return (
      <div className="max-w-6xl px-5 py-8 sm:px-10 sm:py-10">
        <HeldCheckout key={b.id} booking={b} offered={offered}>
          {content}
        </HeldCheckout>
      </div>
    )
  }
  return <div className="max-w-3xl space-y-5 px-5 py-8 sm:px-10 sm:py-10">{content}</div>
}

function PayPanel({ booking }: { booking: Booking }) {
  const { withAuth } = useAuth()
  const queryClient = useQueryClient()
  const seconds = useSecondsUntil(booking.hold_expires_at)
  const pay = usePayForBooking(booking)
  const [outcome, setOutcome] = useState<PayOutcome | null>(null)
  const release = useMutation({
    mutationFn: () => withAuth((token) => releaseHold(token, booking.id)),
    onSuccess: (updated) => {
      queryClient.setQueryData(['booking', booking.id], updated)
      void queryClient.invalidateQueries({ queryKey: ['bookings'] })
      void queryClient.invalidateQueries({ queryKey: ['public-departure', booking.departure.id] })
    },
  })

  const { mutate } = pay
  const start = useCallback(() => {
    setOutcome(null)
    mutate(undefined, { onSuccess: setOutcome })
  }, [mutate])

  const expired = seconds === 0
  const error = pay.error ?? release.error
  return (
    <section className="rounded-(--card-radius) border border-laterite-400/50 bg-laterite-100/60 p-5 sm:p-7">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="font-display text-xl font-medium text-stone-900">
          {expired ? 'Your hold has ended' : 'Seats held for you'}
        </h2>
        {!expired && (
          <span className="font-mono text-lg font-semibold text-laterite-600" aria-live="off">
            {clock(seconds)}
          </span>
        )}
      </div>
      <p className="mt-1 text-sm text-stone-700">
        {expired
          ? "We're checking whether a payment came through. If it didn't, the seats go back to the batch."
          : `Pay ${rupees(booking.amount_paise)} within the time left to confirm. UPI, cards, netbanking and wallets are accepted.`}
      </p>

      {outcome?.kind === 'processing' && (
        <p role="status" className="mt-3 text-sm font-medium text-pine-700">
          Payment received — waiting for the bank to confirm. This page updates on its own.
        </p>
      )}
      {outcome?.kind === 'closed' && (
        <p role="alert" className="mt-3 text-sm text-laterite-600">
          {outcome.lastError ? `${outcome.lastError} ` : 'The payment window was closed. '}
          You can try again while the hold lasts.
        </p>
      )}
      {error && (
        <p role="alert" className="mt-3 text-sm text-laterite-600">
          {/* Non-API errors come from loading checkout.js and carry their own copy. */}
          {error instanceof ApiError ? messageFor(error) : error.message}
        </p>
      )}

      {!expired && (
        <div className="mt-4 flex flex-wrap gap-2">
          <button
            type="button"
            onClick={start}
            disabled={pay.isPending || release.isPending}
            className="rounded-full bg-pine-600 px-6 py-2.5 font-medium text-white hover:bg-pine-700 disabled:opacity-50"
          >
            {pay.isPending ? 'Waiting for payment…' : `Pay ${rupees(booking.amount_paise)}`}
          </button>
          <button
            type="button"
            onClick={() => window.confirm('Give these seats back?') && release.mutate()}
            disabled={pay.isPending || release.isPending}
            className="rounded-full border border-paper-300 bg-paper-50 px-5 py-2.5 text-sm font-medium text-stone-700 hover:border-pine-600 disabled:opacity-50"
          >
            Release seats
          </button>
        </div>
      )}
    </section>
  )
}

/**
 * Saved travellers, else blanks; the booker is usually the first. Before payment our insurance is ticked where it's
 * offered (it's compulsory there); a paid booking keeps what it paid for.
 */
function initialDrafts(b: Booking, offered: OfferedAddon[]): TravellerDraft[] {
  const insure = b.status === 'HELD' && offered.some((a) => a.key === 'insurance')
  return Array.from({ length: b.seats }, (_, i) => {
    const t = b.travellers[i]
    if (t) {
      return {
        full_name: t.full_name,
        phone: t.phone ?? '',
        date_of_birth: t.date_of_birth,
        gender: t.gender,
        insurance: insure || t.insurance,
        insurance_id: insure ? '' : (t.insurance_id ?? ''),
        offloading: t.offloading,
        transport: t.transport,
      }
    }
    const blank = blankTraveller(offered)
    return i === 0 ? { ...blank, full_name: b.contact.full_name ?? '', phone: b.contact.phone ?? '' } : blank
  })
}

/** Names every seat (after payment where no add-ons are on offer). Changeable until the start date; paid add-ons aren't. */
function TravellersSection({ booking: b, offered }: { booking: Booking; offered: OfferedAddon[] }) {
  const { withAuth } = useAuth()
  const queryClient = useQueryClient()
  const editable = (b.status === 'HELD' || b.status === 'CONFIRMED') && todayIst() < b.departure.start_date
  const [editing, setEditing] = useState(editable && b.status === 'CONFIRMED' && !b.travellers_complete)
  const [drafts, setDrafts] = useState<TravellerDraft[]>(() => initialDrafts(b, offered))
  const save = useMutation({
    mutationFn: () =>
      withAuth((token) =>
        updateTravellers(token, b.id, drafts.map(toInput)),
      ),
    onSuccess: (updated) => {
      queryClient.setQueryData(['booking', b.id], updated)
      void queryClient.invalidateQueries({ queryKey: ['bookings'] })
      setEditing(false)
    },
  })
  const errors = fieldErrors(save.error)
  const update = (index: number, patch: Partial<TravellerDraft>) =>
    setDrafts((current) => current.map((t, i) => (i === index ? { ...t, ...patch } : t)))

  if (!editing) {
    return (
      <section className="rounded-(--card-radius) border border-paper-300 bg-paper-50 p-5 sm:p-7">
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <h2 className="font-display text-xl font-medium text-stone-900">Travellers</h2>
          {editable && (
            <button
              type="button"
              onClick={() => setEditing(true)}
              className="text-sm font-medium text-pine-700 hover:text-pine-600"
            >
              {b.travellers_complete ? 'Edit' : 'Add travellers'}
            </button>
          )}
        </div>
        {b.travellers.length === 0 ? (
          <p className="mt-2 text-sm text-stone-600">
            No one named yet — add {b.seats === 1 ? 'the traveller' : `all ${b.seats} travellers`} before the trek.
          </p>
        ) : (
          <ul className="mt-3 divide-y divide-paper-300">
            {b.travellers.map((t, i) => (
              <li key={i} className="flex justify-between gap-3 py-2.5 text-sm">
                <span className="font-medium text-stone-900">{t.full_name}</span>
                <span className="text-right text-stone-500">{addonSummary(t)}</span>
              </li>
            ))}
          </ul>
        )}
      </section>
    )
  }

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault()
        save.mutate()
      }}
      className="rounded-(--card-radius) border border-pine-600/40 bg-paper-50 p-5 sm:p-7"
    >
      <h2 className="font-display text-xl font-medium text-stone-900">Who's coming?</h2>
      <p className="mt-1 text-sm text-stone-600">
        Your guide plans tents, rooms and permits from this. Everyone must be 12 or older on the trek date, and anyone under 18 comes with a parent or guardian on this booking.
      </p>
      {errors.travellers && <p className="mt-2 text-sm text-laterite-600">{errors.travellers}</p>}
      <ol className="mt-5 space-y-6">
        {drafts.map((t, i) => (
          <li key={i} className={i > 0 ? 'border-t border-paper-300 pt-6' : ''}>
            <TravellerFields
              index={i}
              traveller={t}
              update={(patch) => update(i, patch)}
              errors={errors}
              startDate={b.departure.start_date}
              offered={offered}
              addonsLocked={b.status !== 'HELD'}
            />
          </li>
        ))}
      </ol>
      {save.error && Object.keys(errors).length === 0 && (
        <p role="alert" className="mt-4 text-sm text-laterite-600">
          {messageFor(save.error)}
        </p>
      )}
      <div className="mt-5 flex flex-wrap gap-2">
        <button
          type="submit"
          disabled={save.isPending}
          className="rounded-full bg-pine-600 px-6 py-2.5 font-medium text-white hover:bg-pine-700 disabled:opacity-50"
        >
          {save.isPending ? 'Saving…' : 'Save travellers'}
        </button>
        {b.travellers_complete && (
          <button
            type="button"
            onClick={() => setEditing(false)}
            className="rounded-full border border-paper-300 bg-paper-50 px-5 py-2.5 text-sm font-medium text-stone-700 hover:border-pine-600"
          >
            Cancel
          </button>
        )}
      </div>
    </form>
  )
}

/** "Insurance · offloading" or "Own insurance POL-1"; phone when nothing else. */
function addonSummary(t: Booking['travellers'][number]): string {
  const parts = [
    t.insurance ? 'Insurance' : t.insurance_id ? `Own insurance ${t.insurance_id}` : null,
    t.offloading ? 'offloading' : null,
    t.transport ? 'transport' : null,
  ].filter(Boolean)
  return parts.length > 0 ? parts.join(' · ') : (t.phone ?? '')
}

/**
 * Held seats with add-ons on offer: who's coming, each with their insurance (compulsory where offered),
 * offloading and transport, beside a price that follows every tick. Paying saves them first, so the order is
 * for exactly what's shown.
 */
function HeldCheckout({ booking: b, offered, children }: { booking: Booking; offered: OfferedAddon[]; children: ReactNode }) {
  const { withAuth } = useAuth()
  const queryClient = useQueryClient()
  const seconds = useSecondsUntil(b.hold_expires_at)
  const pay = usePayForBooking(b)
  const [outcome, setOutcome] = useState<PayOutcome | null>(null)
  const [drafts, setDrafts] = useState<TravellerDraft[]>(() => initialDrafts(b, offered))
  const save = useMutation({
    mutationFn: () => withAuth((token) => updateTravellers(token, b.id, drafts.map(toInput))),
    onSuccess: (updated) => queryClient.setQueryData(['booking', b.id], updated),
  })
  const release = useMutation({
    mutationFn: () => withAuth((token) => releaseHold(token, b.id)),
    onSuccess: (updated) => {
      queryClient.setQueryData(['booking', b.id], updated)
      void queryClient.invalidateQueries({ queryKey: ['bookings'] })
      void queryClient.invalidateQueries({ queryKey: ['public-departure', b.departure.id] })
    },
  })
  const errors = fieldErrors(save.error)
  const update = (index: number, patch: Partial<TravellerDraft>) =>
    setDrafts((current) => current.map((t, i) => (i === index ? { ...t, ...patch } : t)))

  const fee = b.price_paise_per_seat * b.seats
  const lines = addonLines(drafts, offered)
  const total = fee + lines.reduce((sum, a) => sum + a.count * a.price, 0)

  const expired = seconds === 0
  const busy = save.isPending || pay.isPending || release.isPending
  const payNow = async () => {
    setOutcome(null)
    try {
      await save.mutateAsync()
    } catch {
      return
    }
    pay.mutate(undefined, { onSuccess: setOutcome })
  }
  const error = pay.error ?? release.error ?? (save.error && Object.keys(errors).length === 0 ? save.error : null)

  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_20rem] lg:items-start">
      <div className="min-w-0 space-y-5">
        {children}
        <form
          id="whos-coming"
          onSubmit={(e) => {
            e.preventDefault()
            void payNow()
          }}
          className="rounded-(--card-radius) border border-pine-600/40 bg-paper-50 p-5 sm:p-7"
        >
          <h2 className="font-display text-xl font-medium text-stone-900">Who's coming?</h2>
          <p className="mt-1 text-sm text-stone-600">
            Everyone must be 12 or older on the trek date (under 18 only with a parent or guardian on this booking). Trek insurance is required for everyone.
          </p>
          {errors.travellers && <p className="mt-2 text-sm text-laterite-600">{errors.travellers}</p>}
          <ol className="mt-5 space-y-6">
            {drafts.map((t, i) => (
              <li key={i} className={i > 0 ? 'border-t border-paper-300 pt-6' : ''}>
                <TravellerFields index={i} traveller={t} update={(patch) => update(i, patch)} errors={errors}
                  startDate={b.departure.start_date} offered={offered} addonsLocked={false} />
              </li>
            ))}
          </ol>
        </form>
      </div>

      <aside className="rounded-(--card-radius) border border-laterite-400/50 bg-laterite-100/60 p-5 lg:sticky lg:top-24">
        <div className="flex items-baseline justify-between gap-2">
          <h2 className="font-display text-lg font-medium text-stone-900">{expired ? 'Your hold has ended' : 'Seats held for you'}</h2>
          {!expired && (
            <span className="font-mono text-lg font-semibold text-laterite-600" aria-live="off">
              {clock(seconds)}
            </span>
          )}
        </div>
        <dl className="mt-4 space-y-2 text-sm" aria-live="polite">
          <div className="flex justify-between gap-3">
            <dt className="text-stone-600">Trek fee · {b.seats} × {rupees(b.price_paise_per_seat)}</dt>
            <dd className="font-medium tabular-nums">{rupees(fee)}</dd>
          </div>
          {lines.map((a) => (
            <div key={a.key} className="flex justify-between gap-3">
              <dt className="text-stone-600">{a.label} · {a.count} × {rupees(a.price)}</dt>
              <dd className="font-medium tabular-nums">+ {rupees(a.count * a.price)}</dd>
            </div>
          ))}
          <div className="flex items-baseline justify-between gap-3 border-t border-laterite-400/40 pt-2">
            <dt className="font-semibold text-stone-900">Total</dt>
            <dd className="text-2xl font-semibold tabular-nums">{rupees(total)}</dd>
          </div>
        </dl>

        {outcome?.kind === 'processing' && (
          <p role="status" className="mt-3 text-sm font-medium text-pine-700">
            Payment received — waiting for the bank to confirm. This page updates on its own.
          </p>
        )}
        {outcome?.kind === 'closed' && (
          <p role="alert" className="mt-3 text-sm text-laterite-600">
            {outcome.lastError ? `${outcome.lastError} ` : 'The payment window was closed. '}You can try again while the hold lasts.
          </p>
        )}
        {error && (
          <p role="alert" className="mt-3 text-sm text-laterite-600">
            {error instanceof ApiError ? messageFor(error) : error.message}
          </p>
        )}
        {save.error && Object.keys(errors).length > 0 && (
          <p role="alert" className="mt-3 text-sm text-laterite-600">Check the highlighted traveller details.</p>
        )}

        {expired ? (
          <p className="mt-3 text-sm text-stone-700">We're checking whether a payment came through. If it didn't, the seats go back to the batch.</p>
        ) : (
          <div className="mt-4 space-y-2">
            <button type="submit" form="whos-coming" disabled={busy}
              className="w-full rounded-full bg-pine-600 px-6 py-3 font-medium text-white hover:bg-pine-700 disabled:opacity-50">
              {save.isPending ? 'Saving…' : pay.isPending ? 'Waiting for payment…' : `Pay ${rupees(total)}`}
            </button>
            <button type="button" onClick={() => window.confirm('Give these seats back?') && release.mutate()} disabled={busy}
              className="w-full rounded-full border border-paper-300 bg-paper-50 px-5 py-2.5 text-sm font-medium text-stone-700 hover:border-pine-600 disabled:opacity-50">
              Release seats
            </button>
            <p className="text-center text-xs text-stone-500">UPI, cards, netbanking and wallets.</p>
          </div>
        )}
      </aside>
    </div>
  )
}

const REFUND_KIND_LABEL: Record<Refund['kind'], string> = {
  TREKKER_CANCELLATION: 'Cancellation refund',
  FORCE_MAJEURE: 'Full refund — departure cancelled',
  LATE_CAPTURE: 'Refund — payment arrived after the hold',
}

const REFUND_STATUS_LABEL: Record<Refund['status'], string> = {
  PENDING: 'On its way',
  PROCESSED: 'Refunded',
  FAILED: "Delayed — we're on it",
}

function PaymentSection({ booking: b }: { booking: Booking }) {
  const payment = b.payment
  return (
    <section className="rounded-(--card-radius) border border-paper-300 bg-paper-50 p-5 sm:p-7">
      <h2 className="font-display text-xl font-medium text-stone-900">Payment</h2>
      {payment?.status === 'PAID' && (
        <p className="mt-2 text-sm text-stone-700">
          {rupees(payment.amount_paise)} paid{payment.paid_at && ` on ${dateTime(payment.paid_at)}`}
          {describeMethod(payment) && ` · ${describeMethod(payment)}`}
        </p>
      )}
      {b.refunds.length > 0 && (
        <>
          <ul className="mt-4 divide-y divide-paper-300">
            {b.refunds.map((r) => (
              <li key={r.id} className="flex flex-wrap items-baseline justify-between gap-2 py-2.5 text-sm">
                <span className="text-stone-700">{REFUND_KIND_LABEL[r.kind]}</span>
                <span>
                  <span className="font-semibold">{rupees(r.amount_paise)}</span>
                  <span className={`ml-2 text-xs ${r.status === 'PROCESSED' ? 'text-pine-700' : 'text-stone-500'}`}>
                    {REFUND_STATUS_LABEL[r.status]}
                  </span>
                </span>
              </li>
            ))}
          </ul>
          <p className="mt-2 text-xs text-stone-500">
            Refunds reach cards and netbanking in about 5–7 working days. UPI and wallets are usually faster.
          </p>
        </>
      )}
    </section>
  )
}

function CancelSection({ booking, onKeep }: { booking: Booking; onKeep: () => void }) {
  const { withAuth } = useAuth()
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const quote = useQuery({
    queryKey: ['cancellation-quote', booking.id],
    queryFn: () => withAuth((token) => getCancellationQuote(token, booking.id)),
    enabled: open,
    staleTime: 0,
  })
  const cancel = useMutation({
    mutationFn: () => withAuth((token) => cancelBooking(token, booking.id)),
    onSuccess: (updated) => {
      queryClient.setQueryData(['booking', booking.id], updated)
      void queryClient.invalidateQueries({ queryKey: ['bookings'] })
      void queryClient.invalidateQueries({ queryKey: ['public-departure', booking.departure.id] })
      setOpen(false)
    },
  })
  const tiers = booking.refund_policy ?? []

  return (
    <section id="cancel" className="scroll-mt-20 rounded-(--card-radius) border border-paper-300 bg-paper-50 p-5 sm:p-7">
      <h2 className="font-display text-xl font-medium text-stone-900">Change of plans?</h2>
      <p className="mt-1 text-sm text-stone-600">
        Your batch is confirmed and runs on these dates whatever the numbers. If you cancel, your seats go back to the
        batch and the refund depends on how close to the start you are:
      </p>
      <ul className="mt-2 space-y-1 text-sm text-stone-600">
        {tiers.map((t, i) => {
          const upper = i === 0 ? null : tiers[i - 1].min_days_before - 1
          const range =
            upper === null
              ? `${t.min_days_before}+ days before`
              : t.min_days_before === 0
                ? `Less than ${tiers[i - 1].min_days_before} days before`
                : `${t.min_days_before}–${upper} days before`
          return (
            <li key={t.min_days_before}>
              {range}: {t.refund_bps === 0 ? 'no refund' : `${t.refund_bps / 100}% refund`}
            </li>
          )
        })}
      </ul>

      {!open ? (
        <div className="mt-4 flex flex-wrap items-center gap-4">
          <button
            type="button"
            onClick={onKeep}
            className="rounded-full bg-pine-600 px-5 py-2 text-sm font-medium text-white hover:bg-pine-700"
          >
            Keep my booking
          </button>
          <button
            type="button"
            onClick={() => setOpen(true)}
            className="text-sm text-stone-500 underline underline-offset-2 hover:text-laterite-600"
          >
            Continue to cancel
          </button>
        </div>
      ) : (
        <div className="mt-4 rounded-xl border border-paper-300 bg-paper-100 p-4">
          {quote.isPending ? (
            <p className="text-sm text-stone-600">Working out your refund…</p>
          ) : quote.isError ? (
            <p className="text-sm text-laterite-600">{messageFor(quote.error)}</p>
          ) : !quote.data.allowed ? (
            <p className="text-sm text-stone-700">This booking can no longer be cancelled.</p>
          ) : (
            <>
              <p className="text-sm text-stone-800">
                You're cancelling {quote.data.days_before_start} {quote.data.days_before_start === 1 ? 'day' : 'days'}{' '}
                before the trek.{' '}
                {quote.data.refund_paise > 0 ? (
                  <>
                    You'll get <span className="font-semibold">{rupees(quote.data.refund_paise)}</span> back (
                    {quote.data.refund_bps / 100}%).
                  </>
                ) : (
                  <span className="font-semibold">No refund applies at this point.</span>
                )}{' '}
                Your seats go back to the batch. This can't be undone.
              </p>
              {cancel.error && <p className="mt-2 text-sm text-laterite-600">{messageFor(cancel.error)}</p>}
              <div className="mt-3 flex flex-wrap gap-2">
                <button
                  type="button"
                  onClick={onKeep}
                  disabled={cancel.isPending}
                  className="rounded-full bg-pine-600 px-5 py-2 text-sm font-medium text-white hover:bg-pine-700 disabled:opacity-50"
                >
                  Keep my booking
                </button>
                <button
                  type="button"
                  onClick={() => cancel.mutate()}
                  disabled={cancel.isPending}
                  className="rounded-full border border-laterite-400/60 bg-paper-50 px-5 py-2 text-sm font-medium text-laterite-600 hover:border-laterite-600 disabled:opacity-50"
                >
                  {cancel.isPending ? 'Cancelling…' : 'Yes, cancel my booking'}
                </button>
              </div>
            </>
          )}
        </div>
      )}
    </section>
  )
}

function Fact({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <dt className="text-xs text-stone-500">{label}</dt>
      <dd className="mt-1 text-stone-900">{children}</dd>
    </div>
  )
}

function Banner({ tone, title, children }: { tone: 'good' | 'warn' | 'muted'; title: string; children: ReactNode }) {
  const tones = {
    good: 'border-pine-600/25 bg-pine-600/10 text-pine-700',
    warn: 'border-laterite-400/40 bg-laterite-100 text-laterite-600',
    muted: 'border-paper-300 bg-paper-200/60 text-stone-700',
  }
  return (
    <div role="status" className={`rounded-(--card-radius) border px-5 py-4 text-sm ${tones[tone]}`}>
      <p className="font-semibold">{title}</p>
      <p className="mt-0.5">{children}</p>
    </div>
  )
}

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useId, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { googleSignIn, type User } from '../../api/auth.ts'
import { createBooking, createGuestBooking, listBookings, type Booking } from '../../api/bookings.ts'
import { getDeparture, type DepartureDetail } from '../../api/catalog.ts'
import { ApiError } from '../../api/client.ts'
import { fieldErrors, messageFor } from '../../auth/errorMessages.ts'
import { useAuth } from '../../auth/useAuth.ts'
import { EMAIL_RULE, INDIAN_MOBILE } from '../../auth/validation.ts'
import { FormError } from '../../components/auth/AuthCard.tsx'
import { GoogleSignInButton } from '../../components/auth/GoogleSignInButton.tsx'
import { TextField } from '../../components/auth/TextField.tsx'
import { DifficultyPill, SeatMeter } from '../../components/catalog/DeparturePieces.tsx'
import { OtherDepartures } from '../../components/catalog/OtherDepartures.tsx'
import { GuideLine } from '../../components/catalog/TrekPieces.tsx'
import { dateRange, rupees } from '../../lib/format.ts'

/** The three checkout fields. `digits` is the 10-digit WhatsApp number without +91. */
type Details = { full_name: string; digits: string; email: string }

/** Carried to another departure's checkout (e.g. after "seats just went") so nothing is typed twice. */
type CarryState = { details?: Details; seats?: number }

const EMPTY: Details = { full_name: '', digits: '', email: '' }

/** Accepts pasted numbers like "+91 98765 43210". */
function toDigits(value: string): string {
  const digits = value.replace(/\D/g, '')
  return (digits.length > 10 && digits.startsWith('91') ? digits.slice(2) : digits).slice(0, 10)
}

function fromUser(user: User): Details {
  return {
    full_name: user.full_name ?? '',
    digits: user.phone ? toDigits(user.phone) : '',
    email: user.email ?? '',
  }
}

function fromBooking(booking: Booking): Details {
  const c = booking.contact
  return { full_name: c.full_name ?? '', digits: c.phone ? toDigits(c.phone) : '', email: c.email ?? '' }
}

/** Keeps what's typed; fills only the blanks. */
const fillBlanks = (current: Details, source: Details): Details => ({
  full_name: current.full_name || source.full_name,
  digits: current.digits || source.digits,
  email: current.email || source.email,
})

/** /book/:departureId — public. Guests check out with three fields; nothing is gated behind an account. */
export function BookPage() {
  const { departureId = '' } = useParams()
  const auth = useAuth()
  const departure = useQuery({ queryKey: ['public-departure', departureId], queryFn: () => getDeparture(departureId) })

  if (auth.status === 'loading' || departure.isPending) {
    return (
      <div className="mx-auto max-w-5xl space-y-4 px-4 py-10" aria-busy="true" aria-label="Loading">
        <div className="h-28 animate-pulse rounded-2xl bg-stone-200" />
        <div className="h-72 animate-pulse rounded-2xl bg-stone-100" />
      </div>
    )
  }
  if (departure.isError) {
    return (
      <Notice title="Something went wrong">
        {messageFor(departure.error)}{' '}
        <Link to="/#treks" className="text-brand-700 underline">
          See departures
        </Link>
      </Notice>
    )
  }
  if (auth.status === 'authenticated' && auth.user.role !== 'TREKKER') {
    return (
      <Notice title="Bookings are made from trekker accounts">
        Sign out to book as a guest.{' '}
        <Link to={`/departures/${departureId}`} className="text-brand-700 underline">
          Back to the departure
        </Link>
      </Notice>
    )
  }
  if (!departure.data.bookable) {
    return (
      <section className="mx-auto max-w-md space-y-8 px-4 py-16">
        <div className="text-center">
          <h1 className="font-display text-2xl font-semibold">
            {departure.data.seats_left === 0 ? 'This batch is full' : "This departure isn't taking bookings"}
          </h1>
          <Link to={`/departures/${departureId}`} className="mt-2 inline-block text-brand-700 underline">
            Back to the departure
          </Link>
        </div>
        <OtherDepartures current={departure.data} title="Other dates you can book" />
      </section>
    )
  }
  return <Checkout key={departure.data.id} departure={departure.data} user={auth.user} />
}

function Checkout({ departure, user }: { departure: DepartureDetail; user: User | null }) {
  const auth = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const carried = (useLocation().state as CarryState | null) ?? {}
  const [seats, setSeats] = useState(() => Math.max(1, Math.min(carried.seats ?? 1, departure.seats_left)))
  const [details, setDetails] = useState<Details>(() => fillBlanks(carried.details ?? EMPTY, user ? fromUser(user) : EMPTY))
  const [clientErrors, setClientErrors] = useState<Record<string, string>>({})
  const [googleError, setGoogleError] = useState('')
  /** Seats left as last reported by a failed hold; the departure itself refreshes only after a success. */
  const [knownLeft, setKnownLeft] = useState(departure.seats_left)
  const formId = useId()

  // Signing in with Google mid-form fills whatever is still blank — once per account.
  const filledFor = useRef(user?.id)
  useEffect(() => {
    if (user && filledFor.current !== user.id) {
      filledFor.current = user.id
      setDetails((current) => fillBlanks(current, fromUser(user)))
    }
  }, [user])

  // A returning guest has no contact on the account; their last booking has it.
  const guestBookings = useQuery({
    queryKey: ['bookings'],
    queryFn: () => auth.withAuth(listBookings),
    enabled: user?.guest === true,
  })
  const lastBooking = guestBookings.data?.items[0]
  const filledFromBooking = useRef(false)
  useEffect(() => {
    if (lastBooking && !filledFromBooking.current) {
      filledFromBooking.current = true
      setDetails((current) => fillBlanks(current, fromBooking(lastBooking)))
    }
  }, [lastBooking])

  const book = useMutation({
    mutationFn: async (seatCount: number): Promise<Booking> => {
      const contact = {
        full_name: details.full_name.trim(),
        phone: `+91${details.digits}`,
        email: details.email.trim(),
      }
      if (user) {
        return auth.withAuth((token) => createBooking(token, { departure_id: departure.id, seats: seatCount, ...contact }))
      }
      const result = await createGuestBooking({ departure_id: departure.id, seats: seatCount, ...contact })
      auth.setSession(result.auth)
      return result.booking
    },
    onSuccess: (booking) => {
      void queryClient.invalidateQueries({ queryKey: ['bookings'] })
      void queryClient.invalidateQueries({ queryKey: ['public-departure', departure.id] })
      navigate(`/account/bookings/${booking.id}`, { state: { pay: true } })
    },
    onError: (error) => {
      if (error instanceof ApiError && error.code === 'NOT_ENOUGH_SEATS') {
        setKnownLeft(Number(error.details.seats_left ?? 0))
      }
    },
    // Fresh alternatives either way. The departure itself isn't refetched on failure, which keeps this form
    // (and the "seats just went" panel) on screen even when the batch has filled.
    onSettled: () => void queryClient.invalidateQueries({ queryKey: ['public-departures'] }),
  })

  const serverErrors = fieldErrors(book.error)
  const errors = { ...serverErrors, ...clientErrors }
  const seatsWent =
    book.error instanceof ApiError && book.error.code === 'NOT_ENOUGH_SEATS'
      ? { wanted: book.variables ?? seats, left: Number(book.error.details.seats_left ?? 0) }
      : null
  const existingBooking =
    book.error instanceof ApiError && book.error.code === 'ALREADY_BOOKED'
      ? String(book.error.details.booking_id ?? '')
      : null

  const update = (patch: Partial<Details>) => setDetails((current) => ({ ...current, ...patch }))

  const hold = (seatCount: number) => {
    const found: Record<string, string> = {}
    if (!details.full_name.trim()) found.full_name = 'Enter your name'
    if (!INDIAN_MOBILE.test(details.digits)) found.phone = 'Enter a 10-digit Indian mobile number'
    if (!EMAIL_RULE.test(details.email.trim())) found.email = 'Enter a valid email'
    setClientErrors(found)
    if (Object.keys(found).length === 0) book.mutate(seatCount)
  }

  const submit = (e: FormEvent) => {
    e.preventDefault()
    hold(seats)
  }

  const takeWhatsLeft = (left: number) => {
    setSeats(left)
    hold(left)
  }

  async function onGoogle(idToken: string) {
    setGoogleError('')
    try {
      auth.setSession(await googleSignIn(idToken))
    } catch (err) {
      setGoogleError(messageFor(err))
    }
  }

  const seatOptions = Array.from({ length: Math.min(departure.seats_left, knownLeft) }, (_, i) => i + 1)
  const total = departure.price_paise * seats
  const busy = book.isPending
  const guide = departure.guide

  return (
    <div className="mx-auto max-w-5xl px-4 py-6 sm:py-10">
      <Link to={`/departures/${departure.id}`} className="text-sm text-brand-700 hover:text-brand-900">
        ← {departure.track.name}
      </Link>

      <div className="mt-4 grid gap-5 lg:grid-cols-[1fr_340px] lg:items-start">
        <form id={formId} onSubmit={submit} noValidate className="space-y-5">
          <section className="rounded-2xl border border-stone-200 bg-white p-5 sm:p-7">
            <div className="flex flex-wrap items-center gap-2">
              <DifficultyPill difficulty={departure.track.difficulty} />
              <span className="text-sm text-stone-600">{dateRange(departure.start_date, departure.end_date)}</span>
            </div>
            <h1 className="mt-2 font-display text-2xl font-semibold">{departure.track.name}</h1>
            <div className="mt-3">
              <GuideLine guide={guide} trekName={departure.track.name} />
            </div>
            <div className="mt-4">
              <SeatMeter size={departure.max_group_size} left={departure.seats_left} />
            </div>

            <fieldset className="mt-5">
              <legend className="text-sm font-medium text-stone-800">How many seats?</legend>
              <div className="mt-2 flex flex-wrap gap-2">
                {seatOptions.map((n) => (
                  <button
                    key={n}
                    type="button"
                    onClick={() => setSeats(n)}
                    aria-pressed={seats === n}
                    className={`size-11 rounded-full text-sm font-semibold transition ${
                      seats === n
                        ? 'bg-brand-900 text-white'
                        : 'bg-white text-stone-700 ring-1 ring-stone-300 hover:ring-brand-400'
                    }`}
                  >
                    {n}
                  </button>
                ))}
              </div>
              {errors.seats && <p className="mt-1 text-sm text-laterite-600">{errors.seats}</p>}
            </fieldset>
          </section>

          {seatsWent && (
            <SeatsWent
              departure={departure}
              wanted={seatsWent.wanted}
              left={seatsWent.left}
              busy={busy}
              onTake={takeWhatsLeft}
              carry={{ details, seats: seatsWent.wanted }}
            />
          )}

          <section className="rounded-2xl border border-stone-200 bg-white p-5 sm:p-7">
            <h2 className="font-display text-xl font-semibold">Your details</h2>
            <p className="mt-1 text-sm text-stone-600">
              {user ? 'Check these are right.' : 'No account, no password, no code.'} Traveller names, medical and dietary
              details come after payment.
            </p>

            {!user && (
              <div className="mt-5 space-y-3">
                <GoogleSignInButton onCredential={(token) => void onGoogle(token)} />
                <FormError>{googleError}</FormError>
              </div>
            )}

            <div className="mt-5 grid gap-4 sm:grid-cols-2">
              <div className="sm:col-span-2">
                <TextField
                  label="Full name"
                  name="full_name"
                  autoComplete="name"
                  required
                  maxLength={100}
                  value={details.full_name}
                  onChange={(e) => update({ full_name: e.target.value })}
                  error={errors.full_name}
                />
              </div>
              <TextField
                label="WhatsApp number"
                name="phone"
                type="tel"
                inputMode="numeric"
                autoComplete="tel-national"
                prefix="+91"
                placeholder="98765 43210"
                required
                value={details.digits}
                onChange={(e) => update({ digits: toDigits(e.target.value) })}
                error={errors.phone}
                hint="Your guide reaches you here."
              />
              <TextField
                label="Email"
                name="email"
                type="email"
                autoComplete="email"
                required
                maxLength={254}
                value={details.email}
                onChange={(e) => update({ email: e.target.value })}
                error={errors.email}
                hint="For your receipt."
              />
            </div>
          </section>

          {book.error && !seatsWent && Object.keys(serverErrors).length === 0 && (
            <p role="alert" className="rounded-lg bg-laterite-100 px-4 py-3 text-sm text-laterite-600">
              {messageFor(book.error)}{' '}
              {existingBooking && (
                <Link to={`/account/bookings/${existingBooking}`} className="font-semibold underline">
                  Open your booking
                </Link>
              )}
            </p>
          )}

          {/* On phones the summary sits below, so the button lives with the form. */}
          <div className="lg:hidden">
            <HoldButton formId={formId} seats={seats} total={total} pending={busy} disabled={knownLeft === 0} />
          </div>
        </form>

        <aside className="rounded-2xl border border-stone-200 bg-white p-5 sm:p-6 lg:sticky lg:top-20">
          <h2 className="text-xs font-semibold tracking-[0.14em] text-stone-500 uppercase">Price</h2>
          <dl className="mt-3 space-y-2 text-sm">
            <div className="flex justify-between gap-3">
              <dt className="text-stone-600">
                Trek fee · {seats} × {rupees(departure.price_paise)}
              </dt>
              <dd className="font-medium">{rupees(total)}</dd>
            </div>
            <div className="flex items-baseline justify-between gap-3 border-t border-stone-100 pt-2">
              <dt className="font-semibold text-stone-900">Total</dt>
              <dd className="text-2xl font-semibold">{rupees(total)}</dd>
            </div>
          </dl>
          <ul className="mt-4 space-y-1.5 text-xs text-stone-600">
            <li>✓ Seats are held for 10 minutes once you continue.</li>
            <li>✓ Pay by UPI, card or netbanking.</li>
            <li>✓ Cancel 15+ days before for a 90% refund, 7–14 days for 50%.</li>
            <li>✓ Full refund if weather, permits or safety stop the trek.</li>
          </ul>
          <div className="mt-5 hidden lg:block">
            <HoldButton formId={formId} seats={seats} total={total} pending={busy} disabled={knownLeft === 0} />
          </div>
        </aside>
      </div>
    </div>
  )
}

/** On desktop it sits in the summary column, outside the <form>, so it points at the form by id. */
function HoldButton({
  formId,
  seats,
  total,
  pending,
  disabled,
}: {
  formId: string
  seats: number
  total: number
  pending: boolean
  disabled: boolean
}) {
  return (
    <button
      type="submit"
      form={formId}
      disabled={pending || disabled}
      className="w-full rounded-full bg-laterite-500 px-6 py-3 font-medium text-white shadow-lg shadow-laterite-600/20 hover:bg-laterite-600 disabled:cursor-not-allowed disabled:opacity-50"
    >
      {pending ? 'Holding your seats…' : `Hold ${seats === 1 ? 'seat' : `${seats} seats`} & pay ${rupees(total)}`}
    </button>
  )
}

/** Someone paid for the last seats while this form was open. Nothing was charged; the details stay. */
function SeatsWent({
  departure,
  wanted,
  left,
  busy,
  onTake,
  carry,
}: {
  departure: DepartureDetail
  wanted: number
  left: number
  busy: boolean
  onTake: (seats: number) => void
  carry: CarryState
}) {
  return (
    <section role="alert" className="space-y-5 rounded-2xl border border-laterite-300 bg-laterite-100/50 p-5 sm:p-7">
      <div>
        <h2 className="font-display text-lg font-semibold text-laterite-600">
          {left === 0
            ? 'Those seats just went'
            : `Only ${left} of your ${wanted} seats ${left === 1 ? 'is' : 'are'} still there`}
        </h2>
        <p className="mt-1 text-sm text-stone-700">
          Someone else booked this departure while you were filling in your details. Nothing has been charged, and your
          details are saved.
        </p>
        {left > 0 && (
          <button
            type="button"
            onClick={() => onTake(left)}
            disabled={busy}
            className="mt-4 rounded-full bg-brand-900 px-5 py-2.5 text-sm font-medium text-white hover:bg-brand-800 disabled:opacity-50"
          >
            Take the {left === 1 ? '1 seat' : `${left} seats`}
          </button>
        )}
      </div>
      <OtherDepartures current={departure} title="Here's what else you can book" seats={wanted} bookState={carry} />
    </section>
  )
}

function Notice({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="mx-auto max-w-md px-4 py-16 text-center">
      <h1 className="font-display text-2xl font-semibold">{title}</h1>
      <p className="mt-2 text-stone-600">{children}</p>
    </section>
  )
}

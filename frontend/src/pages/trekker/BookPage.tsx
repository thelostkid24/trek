import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import type { SignupChoices } from '../../analytics/attribution.ts'
import { googleSignIn, type User } from '../../api/auth.ts'
import { createBooking, createGuestBooking, listBookings, updateTravellers, type Booking } from '../../api/bookings.ts'
import { getDeparture, trekQueryOptions, type DepartureDetail } from '../../api/catalog.ts'
import { getProfile, type Gender } from '../../api/profile.ts'
import { ApiError } from '../../api/client.ts'
import { fieldErrors, messageFor } from '../../auth/errorMessages.ts'
import { useAuth } from '../../auth/useAuth.ts'
import { EMAIL_RULE, INDIAN_MOBILE } from '../../auth/validation.ts'
import { FormError } from '../../components/auth/AuthCard.tsx'
import { GoogleSignInButton } from '../../components/auth/GoogleSignInButton.tsx'
import { SignupChoicesFields } from '../../components/auth/SignupChoicesFields.tsx'
import { TextField } from '../../components/auth/TextField.tsx'
import { DifficultyPill } from '../../components/catalog/DeparturePieces.tsx'
import { AddonFields } from '../../components/booking/TravellerFields.tsx'
import { usePayForBooking, type PayOutcome } from '../../components/booking/usePayForBooking.ts'
import { clock, useSecondsUntil } from '../../components/booking/useSecondsUntil.ts'
import { OtherDepartures } from '../../components/catalog/OtherDepartures.tsx'
import { GuideLine } from '../../components/catalog/TrekPieces.tsx'
import { SelectField } from '../../components/profile/fields.tsx'
import { addonsOffered, type OfferedAddon } from '../../lib/addons.ts'
import { dateRange, rupees } from '../../lib/format.ts'
import { SITE_LINKS } from '../../lib/siteLinks.ts'
import { addonLines, blankTraveller, GENDERS, toInput, travellerErrors, type TravellerDraft } from '../../lib/travellers.ts'

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

const MAX_GROUP = 10

/** Someone on the booking, as added to the list: who they are, then their add-ons. Only the primary has an email. */
type Participant = Omit<TravellerDraft, 'full_name' | 'phone'> & { first: string; last: string; digits: string; email: string }

/** The "Add participant" panel. `index` is the participant being edited, or null for a new one. */
type ParticipantForm = Pick<Participant, 'first' | 'last' | 'digits' | 'email' | 'date_of_birth' | 'gender'> & {
  index: number | null
}

const fullName = (p: Pick<Participant, 'first' | 'last'>) => `${p.first.trim()} ${p.last.trim()}`.trim()

function splitName(name: string): [string, string] {
  const parts = name.trim().split(/\s+/)
  return [parts[0] ?? '', parts.slice(1).join(' ')]
}

function emptyForm(details: Details, index: number | null): ParticipantForm {
  const [first, last] = splitName(details.full_name)
  return { index, first, last, digits: details.digits, email: details.email, date_of_birth: '', gender: '' }
}

/** Keeps what's typed; fills only the blanks. */
function fillForm(form: ParticipantForm, known: Details, born: { date_of_birth: string; gender: Gender | '' }): ParticipantForm {
  const [first, last] = splitName(known.full_name)
  return {
    ...form,
    first: form.first || first,
    last: form.last || last,
    digits: form.digits || known.digits,
    email: form.email || known.email,
    date_of_birth: form.date_of_birth || born.date_of_birth,
    gender: form.gender || born.gender,
  }
}

/** Every field the primary participant needs, so they can start out already added. */
const isComplete = (f: ParticipantForm) => Object.keys(formProblems(f, true)).length === 0

function formProblems(f: ParticipantForm, primary: boolean): Record<string, string> {
  const found: Record<string, string> = {}
  if (!f.first.trim()) found.first = 'Enter the first name'
  if (!f.last.trim()) found.last = 'Enter the last name'
  if (primary ? !INDIAN_MOBILE.test(f.digits) : f.digits !== '' && !INDIAN_MOBILE.test(f.digits)) {
    found.digits = 'Enter a 10-digit Indian mobile number'
  }
  if (primary && !EMAIL_RULE.test(f.email.trim())) found.email = 'Enter a valid email'
  if (!f.date_of_birth) found.date_of_birth = 'Enter the date of birth'
  if (!f.gender) found.gender = 'Choose one'
  return found
}

/** A participant from the form; editing keeps their add-ons, a new one starts with our insurance where offered. */
function toParticipant(f: ParticipantForm, offered: OfferedAddon[], previous?: Participant): Participant {
  const { insurance, insurance_id, offloading, transport } = previous ?? blankTraveller(offered)
  const { first, last, digits, email, date_of_birth, gender } = f
  return { first, last, digits, email, date_of_birth, gender, insurance, insurance_id, offloading, transport }
}

const toForm = (p: Participant, index: number): ParticipantForm => ({
  index,
  first: p.first,
  last: p.last,
  digits: p.digits,
  email: p.email,
  date_of_birth: p.date_of_birth,
  gender: p.gender,
})

function toTraveller(p: Participant): TravellerDraft {
  const { date_of_birth, gender, insurance, insurance_id, offloading, transport } = p
  return { full_name: fullName(p), phone: p.digits ? `+91${p.digits}` : '', date_of_birth, gender, insurance, insurance_id, offloading, transport }
}

const toDetails = (p: Participant): Details => ({ full_name: fullName(p), digits: p.digits, email: p.email })

/**
 * /book/:departureId — public. Participants are added one by one, the booker first (already added when the account
 * and profile know everything), each with their add-ons; then Pay now holds a seat per participant and opens Razorpay
 * in one go. There's no seat count to choose. Guests need no account. Medical and dietary details come after payment.
 */
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
  const offered = addonsOffered(departure.track)
  const [participants, setParticipants] = useState<Participant[]>([])
  /** The open "Add participant" panel; the first one added is the primary participant (the booker). */
  const [form, setForm] = useState<ParticipantForm | null>(() => emptyForm(carried.details ?? EMPTY, null))
  const [formErrors, setFormErrors] = useState<Record<string, string>>({})
  const [acceptedTerms, setAcceptedTerms] = useState(false)
  const [payErrors, setPayErrors] = useState<string[]>([])
  const [googleError, setGoogleError] = useState('')
  /** Guest checkout creates an account, so it asks what sign-up asks. */
  const [choices, setChoices] = useState<SignupChoices>({})
  /** Seats left as last reported by a failed hold; the departure itself refreshes only after a success. */
  const [knownLeft, setKnownLeft] = useState(departure.seats_left)
  /** The hold made by the last Pay now; a retry pays for it (with any add-on changes) instead of holding again. */
  const [held, setHeld] = useState<Booking | null>(null)
  const [outcome, setOutcome] = useState<PayOutcome | null>(null)
  const heldSeconds = useSecondsUntil(held?.hold_expires_at ?? null)
  const holdLive = held !== null && heldSeconds > 0
  const pay = usePayForBooking()
  const maxParticipants = Math.min(knownLeft, MAX_GROUP)

  // What we already know about the booker fills the primary participant's blanks: the account, its profile (date of
  // birth, gender) and a returning guest's last booking. Once all of it is known, they start as a participant.
  const profile = useQuery({
    queryKey: ['trekker-profile', user?.id],
    queryFn: () => auth.withAuth(getProfile),
    enabled: user !== null && !user.guest,
  })
  const guestBookings = useQuery({
    queryKey: ['bookings'],
    queryFn: () => auth.withAuth(listBookings),
    enabled: user?.guest === true,
  })
  const lastBooking = guestBookings.data?.items[0]
  const known: Details | null = user ? fillBlanks(fromUser(user), lastBooking ? fromBooking(lastBooking) : EMPTY) : null
  const born: { date_of_birth: string; gender: Gender | '' } = {
    date_of_birth: profile.data?.date_of_birth ?? '',
    gender: profile.data?.gender ?? '',
  }
  const knownKey = known ? [known.full_name, known.digits, known.email, born.date_of_birth, born.gender].join('|') : ''
  // Adjusted while rendering (not in an effect) whenever what we know changes. Only the primary participant's form
  // takes it, and only before anyone is added.
  const [seenKey, setSeenKey] = useState('')
  if (known && knownKey !== seenKey) {
    setSeenKey(knownKey)
    if (participants.length === 0 && (form === null || form.index === null)) {
      const filled = fillForm(form ?? emptyForm(EMPTY, null), known, born)
      if (isComplete(filled)) {
        setParticipants([toParticipant(filled, offered)])
        setForm(null)
      } else {
        setForm(filled)
      }
    }
  }

  const book = useMutation({
    mutationFn: async (list: Participant[]): Promise<Booking> => {
      const primary = list[0]
      const order = {
        departure_id: departure.id,
        seats: list.length,
        full_name: fullName(primary),
        phone: `+91${primary.digits}`,
        email: primary.email.trim(),
        travellers: list.map(toTraveller).map(toInput),
      }
      if (user) {
        return auth.withAuth((token) => createBooking(token, order))
      }
      const result = await createGuestBooking(order, choices)
      auth.setSession(result.auth)
      return result.booking
    },
    onSuccess: (booking) => {
      setHeld(booking)
      void queryClient.invalidateQueries({ queryKey: ['bookings'] })
      void queryClient.invalidateQueries({ queryKey: ['public-departure', departure.id] })
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
  // A retry on a live hold: save the participants' add-ons (it reprices the booking), then pay.
  const save = useMutation({
    mutationFn: (booking: Booking) =>
      auth.withAuth((token) => updateTravellers(token, booking.id, participants.map(toTraveller).map(toInput))),
    onSuccess: setHeld,
  })

  const seatsWent =
    book.error instanceof ApiError && book.error.code === 'NOT_ENOUGH_SEATS'
      ? { wanted: book.variables?.length ?? participants.length, left: Number(book.error.details.seats_left ?? 0) }
      : null
  const existingBooking =
    book.error instanceof ApiError && book.error.code === 'ALREADY_BOOKED'
      ? String(book.error.details.booking_id ?? '')
      : null
  const serverErrors = { ...fieldErrors(book.error), ...fieldErrors(save.error) }

  const addParticipant = () => {
    if (!form) return
    const primary = form.index === 0 || (form.index === null && participants.length === 0)
    const found = formProblems(form, primary)
    setFormErrors(found)
    if (Object.keys(found).length > 0) return
    const next = toParticipant(form, offered, form.index === null ? undefined : participants[form.index])
    setParticipants((current) =>
      form.index === null ? [...current, next] : current.map((p, i) => (i === form.index ? next : p)),
    )
    setForm(null)
    setPayErrors([])
  }
  const editParticipant = (index: number) => {
    setFormErrors({})
    setForm(toForm(participants[index], index))
  }
  const removeParticipant = (index: number) => setParticipants((current) => current.filter((_, i) => i !== index))
  const updateAddons = (index: number, patch: Partial<TravellerDraft>) =>
    setParticipants((current) => current.map((p, i) => (i === index ? { ...p, ...patch } : p)))
  // Functional, so several fields filled at once (browser autofill) don't overwrite each other.
  const editForm = (patch: Partial<ParticipantForm>) => setForm((current) => current && { ...current, ...patch })
  const openNewForm = () => {
    setFormErrors({})
    setForm(emptyForm(EMPTY, null))
  }

  /** Holds a seat for every participant (or updates a live hold), then opens Razorpay. */
  const payNow = async (list: Participant[]) => {
    const problems: string[] = []
    if (list.length === 0) problems.push('Add at least one participant.')
    if (form) problems.push('Finish adding the participant, or cancel it.')
    if (!acceptedTerms) problems.push('Accept the terms and conditions.')
    const addonErrors = travellerErrors(list.map(toTraveller), offered)
    if (Object.keys(addonErrors).length > 0) problems.push('Enter the insurance policy ID, or tick our insurance, for everyone.')
    setPayErrors(problems)
    if (problems.length > 0) return
    setOutcome(null)
    let booking: Booking
    try {
      booking = holdLive && held ? await save.mutateAsync(held) : await book.mutateAsync(list)
    } catch {
      return
    }
    pay.mutate(booking, {
      onSuccess: (result) => {
        if (result.kind === 'closed') setOutcome(result)
        else navigate(`/account/bookings/${booking.id}`)
      },
    })
  }

  const takeWhatsLeft = (left: number) => {
    const kept = participants.slice(0, left)
    setParticipants(kept)
    void payNow(kept)
  }

  async function onGoogle(idToken: string) {
    setGoogleError('')
    try {
      auth.setSession(await googleSignIn(idToken, choices))
    } catch (err) {
      setGoogleError(messageFor(err))
    }
  }

  const count = participants.length
  const fee = departure.price_paise * count
  const lines = addonLines(participants.map(toTraveller), offered)
  const total = fee + lines.reduce((sum, a) => sum + a.count * a.price, 0)
  const busy = book.isPending || save.isPending || pay.isPending
  const stage = book.isPending || save.isPending ? 'holding' : pay.isPending ? 'paying' : null
  const payError = pay.error ?? (save.error && Object.keys(fieldErrors(save.error)).length === 0 ? save.error : null)
  const formIsPrimary = form !== null && (form.index === 0 || (form.index === null && count === 0))
  const carry: CarryState = { details: participants[0] ? toDetails(participants[0]) : undefined, seats: Math.max(1, count) }

  const formPanel = form && (
    <div className="mt-5 rounded-xl border border-stone-200 p-4 sm:p-5">
      <ul className="list-decimal space-y-1 pl-5 text-sm text-stone-600">
        {formIsPrimary ? (
          <>
            <li>Start with yourself: you're the primary participant.</li>
            <li>We send every trek update to this WhatsApp number and email.</li>
          </>
        ) : (
          <li>Add each person who's coming with you, exactly as on their ID.</li>
        )}
        <li>Medical and dietary details come after payment.</li>
      </ul>
      <div className="mt-4 grid gap-4 sm:grid-cols-2">
        <TextField label="First name *" name="p-first" autoComplete={formIsPrimary ? 'given-name' : 'off'} maxLength={50}
          value={form.first} onChange={(e) => editForm({ first: e.target.value })} error={formErrors.first} />
        <TextField label="Last name *" name="p-last" autoComplete={formIsPrimary ? 'family-name' : 'off'} maxLength={50}
          value={form.last} onChange={(e) => editForm({ last: e.target.value })} error={formErrors.last} />
        <TextField label={formIsPrimary ? 'WhatsApp number *' : 'Phone (optional)'} name="p-phone" type="tel"
          inputMode="numeric" prefix="+91" placeholder="98765 43210" autoComplete={formIsPrimary ? 'tel-national' : 'off'}
          value={form.digits} onChange={(e) => editForm({ digits: toDigits(e.target.value) })} error={formErrors.digits} />
        {formIsPrimary && (
          <TextField label="Email *" name="p-email" type="email" autoComplete="email" maxLength={254}
            value={form.email} onChange={(e) => editForm({ email: e.target.value })} error={formErrors.email} />
        )}
        <TextField label="Date of birth *" name="p-dob" type="date" max={departure.start_date}
          value={form.date_of_birth} onChange={(e) => editForm({ date_of_birth: e.target.value })}
          error={formErrors.date_of_birth} />
        <SelectField label="Gender *" name="p-gender" value={form.gender} options={GENDERS}
          onChange={(e) => editForm({ gender: e.target.value as Gender | '' })} error={formErrors.gender} />
      </div>
      {formIsPrimary && !user && (
        <div className="mt-5 border-t border-stone-100 pt-5">
          <SignupChoicesFields value={choices} onChange={setChoices} />
        </div>
      )}
      <div className="mt-5 flex gap-2">
        <button type="button" onClick={addParticipant}
          className="flex flex-1 items-center justify-center gap-2 rounded-full bg-brand-900 px-5 py-3 font-medium text-white hover:bg-brand-800">
          {form.index === null ? '+ Add participant' : 'Save participant'}
        </button>
        {(count > 0 || form.index !== null) && (
          <button type="button" onClick={() => setForm(null)}
            className="rounded-full border border-stone-300 px-5 py-3 text-sm font-medium text-stone-700 hover:border-stone-500">
            Cancel
          </button>
        )}
      </div>
    </div>
  )

  return (
    <div className="mx-auto max-w-5xl px-4 py-6 sm:py-10">
      <Link to={`/departures/${departure.id}`} className="text-sm text-brand-700 hover:text-brand-900">
        ← {departure.track.name}
      </Link>

      <div className="mt-4 grid gap-5 lg:grid-cols-[1fr_340px] lg:items-start">
        <div className="space-y-5">
          <section className="rounded-2xl border border-stone-200 bg-white p-5 sm:p-7">
            <div className="flex flex-wrap items-center gap-2">
              <DifficultyPill difficulty={departure.track.difficulty} />
              <span className="text-sm text-stone-600">{dateRange(departure.start_date, departure.end_date)}</span>
            </div>
            <h1 className="mt-2 font-display text-2xl font-semibold">{departure.track.name}</h1>
            <div className="mt-3">
              <GuideLine guide={departure.guide} trekName={departure.track.name} />
            </div>
          </section>

          {seatsWent && (
            <SeatsWent
              departure={departure}
              wanted={seatsWent.wanted}
              left={seatsWent.left}
              busy={busy}
              onTake={takeWhatsLeft}
              carry={carry}
            />
          )}

          <section className="rounded-2xl border border-stone-200 bg-white p-5 sm:p-7">
            <h2 className="flex items-center gap-2 font-display text-xl font-semibold">
              <PersonIcon className="size-5 text-laterite-600" />
              Add participants
            </h2>

            {!user && count === 0 && (
              <div className="mt-4 space-y-3">
                <GoogleSignInButton onCredential={(token) => void onGoogle(token)} />
                <FormError>{googleError}</FormError>
              </div>
            )}

            {count === 0 && formPanel}

            {count > 0 && (
              <>
                <h3 className="mt-6 text-sm font-semibold text-stone-800">Participant details</h3>
                <ol className="mt-3 space-y-3">
                  {participants.map((p, i) => (
                    <li key={i} className="overflow-hidden rounded-xl border border-stone-200 border-l-4 border-l-laterite-500">
                      <div className="flex items-start justify-between gap-3 p-4">
                        <div className="flex min-w-0 items-start gap-3">
                          <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-laterite-100 text-laterite-600">
                            <PersonIcon className="size-4" />
                          </span>
                          <div className="min-w-0">
                            <p className="font-semibold text-stone-900">
                              {fullName(p)}
                              {i === 0 && <span className="ml-2 text-xs font-medium text-stone-500">Primary</span>}
                            </p>
                            <p className="truncate text-sm text-stone-600">
                              {[i === 0 ? p.email : null, p.digits ? `+91 ${p.digits}` : null].filter(Boolean).join(' · ')}
                            </p>
                          </div>
                        </div>
                        {!holdLive && (
                          <div className="flex shrink-0 gap-3 text-sm font-medium">
                            <button type="button" onClick={() => editParticipant(i)} className="text-brand-800 hover:text-brand-900">
                              Edit
                            </button>
                            {i > 0 && (
                              <button type="button" onClick={() => removeParticipant(i)} className="text-stone-500 hover:text-laterite-600">
                                Remove
                              </button>
                            )}
                          </div>
                        )}
                      </div>
                      {offered.length > 0 && (
                        <div className="border-t border-stone-100 bg-paper-100/60 p-4">
                          <p className="mb-3 text-xs font-semibold tracking-[0.12em] text-laterite-600 uppercase">Add-ons</p>
                          <AddonFields index={i} traveller={toTraveller(p)} update={(patch) => updateAddons(i, patch)}
                            errors={serverErrors} offered={offered} locked={false} />
                        </div>
                      )}
                    </li>
                  ))}
                </ol>
                {formPanel}
                {!form && !holdLive && count < maxParticipants && (
                  <button type="button" onClick={openNewForm}
                    className="mt-4 w-full rounded-full border border-dashed border-brand-700 px-5 py-3 font-medium text-brand-800 hover:bg-brand-50">
                    + Add participant
                  </button>
                )}
                {!form && !holdLive && count >= maxParticipants && (
                  <p className="mt-4 rounded-xl bg-paper-100 px-4 py-3 text-sm text-stone-600">
                    {maxParticipants === 1 ? 'Only 1 seat is' : `Only ${maxParticipants} seats are`} left on this date, so no
                    one else can be added.{' '}
                    <Link to={`/treks/${departure.track.slug}`} className="font-medium text-brand-800 underline">
                      See other dates
                    </Link>
                  </p>
                )}
                {holdLive && (
                  <p className="mt-3 text-sm text-stone-600">
                    Your {count === 1 ? 'seat is' : `${count} seats are`} held for{' '}
                    <span className="font-mono font-semibold text-laterite-600">{clock(heldSeconds)}</span> while you pay.
                  </p>
                )}
              </>
            )}
          </section>
        </div>

        <aside className="rounded-2xl border border-stone-200 bg-white p-5 sm:p-6 lg:sticky lg:top-20">
          <h2 className="font-display text-lg font-semibold">₹ Payment summary</h2>
          <dl className="mt-3 space-y-2 text-sm">
            <div className="flex justify-between gap-3">
              <dt className="text-stone-600">No. of participants</dt>
              <dd className="font-medium">{count === 1 ? '1 person' : `${count} people`}</dd>
            </div>
            <div className="flex justify-between gap-3">
              <dt className="text-stone-600">Price × {rupees(departure.price_paise)}</dt>
              <dd className="font-medium">{rupees(fee)}</dd>
            </div>
            {lines.map((a) => (
              <div key={a.key} className="flex justify-between gap-3">
                <dt className="text-stone-600">
                  {a.label} · {a.count} × {rupees(a.price)}
                </dt>
                <dd className="font-medium">+{rupees(a.count * a.price)}</dd>
              </div>
            ))}
            <div className="flex items-baseline justify-between gap-3 border-t border-stone-100 pt-2">
              <dt className="font-semibold text-stone-900">Total amount</dt>
              <dd className="text-2xl font-semibold">{rupees(total)}</dd>
            </div>
          </dl>
          {count > 0 && <CharityShare slug={departure.track.slug} fee={fee} />}
          <label className="mt-4 flex cursor-pointer items-start gap-2 text-sm text-stone-700">
            <input type="checkbox" checked={acceptedTerms} onChange={(e) => setAcceptedTerms(e.target.checked)}
              className="mt-0.5 size-4 accent-brand-800" />
            <span>
              I accept the terms and conditions ·{' '}
              <Link to={SITE_LINKS.terms} target="_blank" className="font-medium text-brand-800 underline">
                Read T&amp;C
              </Link>
            </span>
          </label>
          <ul className="mt-4 space-y-1.5 text-xs text-stone-600">
            <li>✓ Your seats are held for 10 minutes while you pay.</li>
            <li>✓ Pay by UPI, card or netbanking.</li>
            <li>✓ Cancel 15+ days before for a 90% refund, 7–14 days for 50%.</li>
            <li>✓ Full refund if weather, permits or safety stop the trek.</li>
          </ul>
          <div className="mt-5 space-y-2 empty:hidden">
            <Alerts
              problems={payErrors}
              outcome={outcome}
              payError={payError}
              bookError={book.error && !seatsWent ? book.error : null}
              existingBooking={existingBooking}
            />
          </div>
          <button
            type="button"
            onClick={() => void payNow(participants)}
            disabled={stage !== null || knownLeft === 0}
            className="mt-5 w-full rounded-full bg-laterite-500 px-6 py-3 font-medium text-white shadow-lg shadow-laterite-600/20 hover:bg-laterite-600 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {stage === 'holding' ? 'Holding your seats…' : stage === 'paying' ? 'Waiting for payment…' : count > 0 ? `Pay ${rupees(total)} now` : 'Pay now'}
          </button>
        </aside>
      </div>
    </div>
  )
}

function Alerts({
  problems,
  outcome,
  payError,
  bookError,
  existingBooking,
}: {
  problems: string[]
  outcome: PayOutcome | null
  payError: Error | null
  bookError: Error | null
  existingBooking: string | null
}) {
  const box = 'rounded-lg bg-laterite-100 px-4 py-3 text-sm text-laterite-600'
  return (
    <>
      {problems.length > 0 && (
        <ul role="alert" className={`${box} list-disc space-y-0.5 pl-8`}>
          {problems.map((p) => (
            <li key={p}>{p}</li>
          ))}
        </ul>
      )}
      {outcome?.kind === 'closed' && (
        <p role="alert" className={box}>
          {outcome.lastError ? `${outcome.lastError} ` : 'The payment window was closed. '}
          Nothing was charged. Everyone's details are saved; press Pay now to try again.
        </p>
      )}
      {payError && (
        <p role="alert" className={box}>
          {/* Non-API errors come from loading checkout.js and carry their own copy. */}
          {payError instanceof ApiError ? messageFor(payError) : payError.message}
        </p>
      )}
      {bookError && (
        <p role="alert" className={box}>
          {messageFor(bookError)}{' '}
          {existingBooking && (
            <Link to={`/account/bookings/${existingBooking}`} className="font-semibold underline">
              Open your booking
            </Link>
          )}
        </p>
      )}
    </>
  )
}

function PersonIcon({ className = '' }: { className?: string }) {
  return (
    <svg viewBox="0 0 20 20" className={className} fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden="true">
      <circle cx="10" cy="7" r="3" />
      <path d="M4 17c.8-3.2 3.1-5 6-5s5.2 1.8 6 5" strokeLinecap="round" />
    </svg>
  )
}

/**
 * Where part of the money goes, set apart just before they pay. The share is of the trek fee only, never the
 * add-ons: "₹104.50 of your ₹10,450 trek fee goes to …".
 */
function CharityShare({ slug, fee }: { slug: string; fee: number }) {
  const trek = useQuery(trekQueryOptions(slug))
  const charity = trek.data?.charity
  if (!charity) return null
  return (
    <div className="mt-4 flex gap-3 rounded-xl bg-laterite-100 px-4 py-3 ring-1 ring-laterite-400/40">
      <svg viewBox="0 0 24 24" className="mt-0.5 size-5 shrink-0 text-laterite-600" fill="currentColor" aria-hidden="true">
        <path d="M12 20.5s-7.5-4.6-7.5-10.2A4.3 4.3 0 0 1 12 7.6a4.3 4.3 0 0 1 7.5 2.7c0 5.6-7.5 10.2-7.5 10.2Z" />
      </svg>
      <p className="text-sm text-stone-800">
        <span className="font-semibold text-laterite-600">{rupees(Math.round((fee * charity.bps) / 10_000))}</span> of your{' '}
        {rupees(fee)} trek fee goes to <span className="font-semibold">{charity.name}</span> ({charity.bps / 100}%), from the price, not on top.
      </p>
    </div>
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

import { ApiError } from '../api/client.ts'

/** Human wording for the error codes in docs/TRD.md §7.2 onwards. */
const MESSAGES: Record<string, string> = {
  NETWORK_ERROR: "Can't reach the server. Check your connection and try again.",
  EMAIL_ALREADY_REGISTERED: 'An account with this email already exists. Try signing in instead.',
  INVALID_CREDENTIALS: 'Email or password is incorrect.',
  ACCOUNT_DISABLED: 'This account has been disabled. Please contact support.',
  TOO_MANY_ATTEMPTS: 'Too many failed attempts. Try again in a few minutes.',
  OTP_RATE_LIMITED: 'Please wait a moment before requesting another code.',
  OTP_INVALID: 'That code is incorrect.',
  OTP_EXPIRED: 'That code expired. Request a new one.',
  OTP_TOO_MANY_ATTEMPTS: 'Too many wrong codes. Request a new one.',
  GOOGLE_TOKEN_INVALID: "Google sign-in didn't work. Try another way to sign in.",
  REFRESH_TOKEN_INVALID: 'Your session expired. Please sign in again.',
  VALIDATION_FAILED: 'Please check the highlighted fields.',
  UNAUTHENTICATED: 'Please sign in again.',
  FORBIDDEN: "You don't have access to this.",
  UNSUPPORTED_IMAGE: 'Please choose a JPEG or PNG photo.',
  FILE_TOO_LARGE: 'That photo is over 5 MB. Please choose a smaller one.',
  EMAIL_ALREADY_VERIFIED: 'This email is already verified.',
  EMAIL_RATE_LIMITED: 'Too many emails sent. Try again a little later.',
  EMAIL_TOKEN_INVALID: 'This link is invalid or has already been used.',
  EMAIL_TOKEN_EXPIRED: 'This link has expired. Request a new one from your profile.',
  PHONE_ALREADY_REGISTERED: 'Another account already uses this number.',
  PHONE_ALREADY_VERIFIED: 'This number is already verified on your account.',
  CURRENT_PASSWORD_INCORRECT: 'Your current password is incorrect.',
  EMAIL_REQUIRED: 'Add an email first — you sign in with it when using a password.',
  // Catalog (§7.5)
  NOT_FOUND: "We couldn't find that.",
  DEPARTURE_NOT_FOUND: 'This departure is not available.',
  TRACK_NOT_FOUND: 'That track no longer exists.',
  GUIDE_NOT_FOUND: "We couldn't find that guide.",
  SLUG_TAKEN: 'Another track already uses this slug.',
  TRACK_IN_USE: "The duration can't change once the track has departures.",
  NOT_A_GUIDE: 'The selected person is not an active guide.',
  DEPARTURE_NOT_DRAFT: 'Only drafts can be changed. Refresh to see the latest status.',
  START_DATE_TOO_SOON: 'The start date is too close to publish. Pick a later date.',
  DEPARTURE_NOT_CANCELLABLE: 'Only a published departure that has not started can be cancelled.',
  USER_NOT_FOUND: 'No active account uses this email. Ask them to sign up first.',
  ALREADY_GUIDE: 'This account is already a guide.',
  ROLE_NOT_PROMOTABLE: "Admins can't be made guides.",
  // Bookings & payments (§7.4, §7.6)
  DEPARTURE_NOT_BOOKABLE: "This departure isn't taking bookings any more.",
  NOT_ENOUGH_SEATS: 'Not enough seats left on this departure.',
  ALREADY_BOOKED: 'You already have a booking on this departure.',
  BOOKING_NOT_FOUND: "We couldn't find that booking.",
  BOOKING_NOT_HELD: 'This booking is no longer waiting for payment.',
  BOOKING_NOT_PAYABLE: 'The seat hold has ended. Start a new booking to pay.',
  TRAVELLERS_LOCKED: "Traveller details can't be changed on this booking any more.",
  BOOKING_NOT_CANCELLABLE: "This booking can't be cancelled.",
  PAYMENT_NOT_FOUND: "We couldn't find that payment.",
  PAYMENT_SIGNATURE_INVALID: "We couldn't verify the payment. If money left your account, it will be refunded.",
  GATEWAY_UNAVAILABLE: 'Payments are unavailable right now. Please try again in a few minutes.',
}

export function messageFor(error: unknown): string {
  if (!(error instanceof ApiError)) return 'Something went wrong. Please try again.'
  if (error.code === 'NOT_ENOUGH_SEATS') {
    const left = error.details.seats_left
    if (typeof left === 'number') {
      return left === 0 ? 'This batch just filled up.' : `Only ${left} ${left === 1 ? 'seat is' : 'seats are'} left.`
    }
  }
  if (error.code === 'OTP_INVALID') {
    const left = error.details.attempts_left
    return typeof left === 'number' && left > 0
      ? `That code is incorrect. ${left} ${left === 1 ? 'attempt' : 'attempts'} left.`
      : MESSAGES.OTP_INVALID
  }
  return MESSAGES[error.code] ?? error.message
}

/** Per-field messages from a 400 VALIDATION_FAILED body: { details: { fields: { email: "..." } } }. */
export function fieldErrors(error: unknown): Record<string, string> {
  if (!(error instanceof ApiError) || error.code !== 'VALIDATION_FAILED') return {}
  const fields = error.details.fields
  if (!fields || typeof fields !== 'object') return {}
  return Object.fromEntries(
    Object.entries(fields as Record<string, unknown>).map(([key, value]) => [key, String(value)]),
  )
}

/** Seconds to wait, from a 429 OTP_RATE_LIMITED / EMAIL_RATE_LIMITED body. */
export function retryAfter(error: unknown): number | null {
  if (!(error instanceof ApiError)) return null
  const seconds = error.details.retry_after
  return typeof seconds === 'number' ? seconds : null
}

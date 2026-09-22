// Client-side mirrors of the server rules in docs/TRD.md §7.2, so most mistakes are caught before a round trip.
// The server stays the authority.

export const EMAIL_RULE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

/** 10-digit Indian mobile, without the +91 prefix. */
export const INDIAN_MOBILE = /^[6-9]\d{9}$/

export const PASSWORD_RULE = /^(?=.*[A-Za-z])(?=.*\d).{8,72}$/
export const PASSWORD_HINT = '8–72 characters, with at least one letter and one digit.'

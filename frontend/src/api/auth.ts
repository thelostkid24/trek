import { acquisition, type SignupChoices } from '../analytics/attribution.ts'
import { apiFetch } from './client'

// Contract: docs/TRD.md §7.2. Every auth call sends credentials so the
// httpOnly refresh cookie is set/sent. Keep the access token in memory only.

export type Role = 'TREKKER' | 'GUIDE' | 'ADMIN'
export type AuthMethod = 'PASSWORD' | 'PHONE_OTP' | 'GOOGLE'

export type User = {
  id: string
  full_name: string | null
  email: string | null
  phone: string | null
  /** Public URL of the profile photo; changes on every upload. */
  avatar_url: string | null
  role: Role
  email_verified: boolean
  phone_verified: boolean
  /** Checked out as a guest: no email, phone or Google yet, so signing out loses access. */
  guest: boolean
  auth_methods: AuthMethod[]
  /** Agreed to trek offers on this channel (docs/TRD.md §7.15). */
  marketing_email: boolean
  marketing_whatsapp: boolean
  created_at: string
}

export type AuthResponse = {
  access_token: string
  token_type: 'Bearer'
  expires_in: number
  is_new_user: boolean
  user: User
}

export type OtpRequestResponse = { expires_in: number; resend_after: number }

/** Error codes the auth endpoints can return (besides VALIDATION_FAILED / NETWORK_ERROR). */
export type AuthErrorCode =
  | 'EMAIL_ALREADY_REGISTERED'
  | 'INVALID_CREDENTIALS'
  | 'ACCOUNT_DISABLED'
  | 'TOO_MANY_ATTEMPTS'
  | 'OTP_RATE_LIMITED'
  | 'OTP_INVALID'
  | 'OTP_EXPIRED'
  | 'OTP_TOO_MANY_ATTEMPTS'
  | 'GOOGLE_TOKEN_INVALID'
  | 'REFRESH_TOKEN_INVALID'
  | 'UNAUTHENTICATED'
  | 'TOKEN_EXPIRED'
  | 'EMAIL_TOKEN_INVALID'
  | 'EMAIL_TOKEN_EXPIRED'

export type SignupRequest = { full_name: string; email: string; password: string }
export type LoginRequest = { email: string; password: string }
export type OtpVerifyRequest = { phone: string; code: string; full_name?: string }

const post = <T>(path: string, body?: unknown) =>
  apiFetch<T>(path, { method: 'POST', body, credentials: 'include' })

// Sign-up and sign-in carry where the visitor came from; the server keeps it only for a new account.
export const signup = (req: SignupRequest, choices?: SignupChoices) =>
  post<AuthResponse>('/api/auth/signup', { ...req, acquisition: acquisition(choices) })

export const login = (req: LoginRequest) => post<AuthResponse>('/api/auth/login', req)

export const requestOtp = (phone: string) => post<OtpRequestResponse>('/api/auth/otp/request', { phone })

export const verifyOtp = (req: OtpVerifyRequest, choices?: SignupChoices) =>
  post<AuthResponse>('/api/auth/otp/verify', { ...req, acquisition: acquisition(choices) })

export const googleSignIn = (idToken: string, choices?: SignupChoices) =>
  post<AuthResponse>('/api/auth/google', { id_token: idToken, acquisition: acquisition(choices) })

/** Restores/extends the session from the refresh cookie. */
export const refresh = () => post<AuthResponse>('/api/auth/refresh')

export const logout = () => post<void>('/api/auth/logout')

/** Opens an email-verification link (docs/TRD.md §7.3). Works signed in or not. */
export const verifyEmail = (token: string) => post<{ email: string }>('/api/auth/email/verify', { token })

export const getMe = (token: string) =>
  apiFetch<User>('/api/auth/me', { token, credentials: 'include' })

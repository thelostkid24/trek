import type { AuthResponse, OtpRequestResponse, User } from './auth'
import { apiFetch } from './client'

// Contract: docs/TRD.md §7.3 — the signed-in user's own account. Call through `withAuth` from useAuth().

export const MAX_AVATAR_BYTES = 5 * 1024 * 1024
export const AVATAR_TYPES = ['image/jpeg', 'image/png']

export function uploadAvatar(token: string, file: File) {
  const body = new FormData()
  body.append('file', file)
  return apiFetch<User>('/api/account/avatar', { method: 'PUT', token, body })
}

export const removeAvatar = (token: string) => apiFetch<User>('/api/account/avatar', { method: 'DELETE', token })

/** Emails a verification link to `email` — the current address (to verify it) or a new one (to switch). */
export const requestEmailChange = (token: string, email: string) =>
  apiFetch<{ expires_in: number }>('/api/account/email', { method: 'POST', token, body: { email } })

export const requestPhoneCode = (token: string, phone: string) =>
  apiFetch<OtpRequestResponse>('/api/account/phone/otp', { method: 'POST', token, body: { phone } })

export const verifyPhone = (token: string, phone: string, code: string) =>
  apiFetch<User>('/api/account/phone/verify', { method: 'POST', token, body: { phone, code } })

export type PasswordChange = { current_password?: string; new_password: string }

/** Signs out every other session and returns a fresh one, so the refresh cookie must be accepted. */
export const changePassword = (token: string, body: PasswordChange) =>
  apiFetch<AuthResponse>('/api/account/password', { method: 'PUT', token, body, credentials: 'include' })

import { useCallback } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import type { AuthResponse } from '../api/auth.ts'
import { useAuth } from './useAuth.ts'

/** Where the user was headed before being sent to sign in (set by guarded routes as `state.from`). */
export function useRedirectTarget(): string {
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from
  return from && from.startsWith('/') && !from.startsWith('//') ? from : '/'
}

export const PROFILE_PATH = '/account/profile'

/** Stores the session and leaves the auth screen. Trekkers without a name go fill in their profile first. */
export function useCompleteSignIn(): (response: AuthResponse) => void {
  const { setSession } = useAuth()
  const navigate = useNavigate()
  const target = useRedirectTarget()

  return useCallback(
    (response: AuthResponse) => {
      setSession(response)
      const { user } = response
      if (!user.full_name && user.role === 'TREKKER') {
        navigate(PROFILE_PATH, { replace: true, state: { welcome: true } })
      } else {
        navigate(target, { replace: true })
      }
    },
    [setSession, navigate, target],
  )
}

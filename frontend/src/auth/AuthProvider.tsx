import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { logout as logoutRequest, refresh, type AuthResponse, type User } from '../api/auth.ts'
import { ApiError } from '../api/client.ts'
import { AuthContext, type AuthState } from './context.ts'

/**
 * Holds the access token in memory only — never localStorage, so a stolen token dies with the tab.
 * The refresh token lives in the httpOnly cookie, which is why a page load can restore the session.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: 'loading', user: null })
  const accessToken = useRef<string | null>(null)

  const setSession = useCallback((response: AuthResponse) => {
    accessToken.current = response.access_token
    setState({ status: 'authenticated', user: response.user })
  }, [])

  const updateUser = useCallback((user: User) => {
    setState((current) => (current.status === 'authenticated' ? { status: 'authenticated', user } : current))
  }, [])

  const clear = useCallback(() => {
    accessToken.current = null
    setState({ status: 'anonymous', user: null })
  }, [])

  // Restore the session from the refresh cookie on first load.
  useEffect(() => {
    let cancelled = false
    refresh()
      .then((response) => {
        if (!cancelled) setSession(response)
      })
      .catch(() => {
        if (!cancelled) clear()
      })
    return () => {
      cancelled = true
    }
  }, [setSession, clear])

  const signOut = useCallback(async () => {
    try {
      await logoutRequest()
    } finally {
      clear()
    }
  }, [clear])

  const withAuth = useCallback(
    async <T,>(call: (token: string) => Promise<T>): Promise<T> => {
      if (!accessToken.current) throw new ApiError(401, 'UNAUTHENTICATED', 'Please sign in')
      try {
        return await call(accessToken.current)
      } catch (error) {
        if (!(error instanceof ApiError) || error.code !== 'TOKEN_EXPIRED') throw error
        const renewed = await refresh().catch((refreshError) => {
          clear()
          throw refreshError
        })
        setSession(renewed)
        return call(renewed.access_token)
      }
    },
    [setSession, clear],
  )

  return (
    <AuthContext.Provider value={{ ...state, setSession, updateUser, signOut, withAuth }}>{children}</AuthContext.Provider>
  )
}

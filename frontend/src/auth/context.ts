import { createContext } from 'react'
import type { AuthResponse, User } from '../api/auth.ts'

export type AuthState =
  | { status: 'loading'; user: null }
  | { status: 'anonymous'; user: null }
  | { status: 'authenticated'; user: User }

export type AuthContextValue = AuthState & {
  /** Stores the session returned by any sign-in endpoint. */
  setSession: (response: AuthResponse) => void
  /** Replaces the in-memory user after an account change (name, photo, phone, email). */
  updateUser: (user: User) => void
  signOut: () => Promise<void>
  /**
   * Runs an authenticated call with the in-memory access token, refreshing once if it has expired.
   * Features added later should go through this rather than holding the token themselves.
   */
  withAuth: <T>(call: (accessToken: string) => Promise<T>) => Promise<T>
}

export const AuthContext = createContext<AuthContextValue | null>(null)

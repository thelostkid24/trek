import type { ReactNode } from 'react'
import { Link, Navigate, useLocation } from 'react-router-dom'
import type { Role } from '../api/auth.ts'
import { useAuth } from './useAuth.ts'

const AUDIENCE: Record<Role, string> = { TREKKER: 'trekkers', GUIDE: 'guides', ADMIN: 'admins' }

/** Route guard: visitors go to /login and come back afterwards; the wrong role sees a short notice. */
export function RequireAuth({ role, children }: { role?: Role; children: ReactNode }) {
  const auth = useAuth()
  const location = useLocation()

  if (auth.status === 'loading') {
    return (
      <div className="flex justify-center py-24" role="status" aria-label="Loading">
        <span className="size-6 animate-spin rounded-full border-2 border-brand-200 border-t-brand-700" />
      </div>
    )
  }
  if (auth.status === 'anonymous') {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  }
  if (role && auth.user.role !== role) {
    return (
      <section className="mx-auto max-w-md px-4 py-16 text-center">
        <h1 className="font-display text-2xl font-semibold">This page is for {AUDIENCE[role]}</h1>
        <p className="mt-2 text-stone-600">Your account doesn't have access to it.</p>
        <Link to="/" className="mt-4 inline-block text-brand-700 underline">
          Back home
        </Link>
      </section>
    )
  }
  return children
}

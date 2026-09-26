import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { getMe, verifyEmail } from '../api/auth.ts'
import { ApiError } from '../api/client.ts'
import { messageFor } from '../auth/errorMessages.ts'
import { PROFILE_PATH } from '../auth/useCompleteSignIn.ts'
import { useAuth } from '../auth/useAuth.ts'
import { AuthCard } from '../components/auth/AuthCard.tsx'

/** Landing page for the emailed link: /account/verify-email?token=… — works signed in or not. */
export function VerifyEmailPage() {
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const auth = useAuth()
  const queryClient = useQueryClient()

  // A query (not an effect) so React StrictMode's double render can't spend the single-use token twice.
  const result = useQuery({
    queryKey: ['verify-email', token],
    queryFn: () => verifyEmail(token),
    enabled: token !== '',
    retry: false,
    staleTime: Infinity,
    gcTime: Infinity,
  })

  const signedIn = auth.status === 'authenticated'
  const { withAuth, updateUser } = auth
  const verified = result.isSuccess
  useEffect(() => {
    if (!verified || !signedIn) return
    withAuth(getMe)
      .then((user) => {
        updateUser(user)
        void queryClient.invalidateQueries({ queryKey: ['trekker-profile', user.id] })
      })
      .catch(() => {})
  }, [verified, signedIn, withAuth, updateUser, queryClient])

  const nextLink = signedIn ? (
    <Link to={PROFILE_PATH} className="font-medium text-pine-600 hover:text-pine-700">
      Back to your profile
    </Link>
  ) : (
    <Link to="/login" className="font-medium text-pine-600 hover:text-pine-700">
      Sign in
    </Link>
  )

  if (!token || result.isError) {
    const expired = result.error instanceof ApiError && result.error.code === 'EMAIL_TOKEN_EXPIRED'
    return (
      <AuthCard
        title={expired ? 'This link has expired' : "We couldn't verify this link"}
        subtitle={token ? messageFor(result.error) : 'The link is missing its token.'}
        footer={nextLink}
      >
        <p className="text-sm text-stone-700">
          You can request a fresh link from the <span className="font-medium">Sign-in &amp; security</span> section of
          your profile.
        </p>
      </AuthCard>
    )
  }

  if (result.isPending) {
    return (
      <AuthCard title="Verifying your email…" subtitle="This only takes a moment." footer={null}>
        <div className="flex justify-center py-4" role="status" aria-label="Verifying">
          <span className="size-6 animate-spin rounded-full border-2 border-paper-300 border-t-pine-600" />
        </div>
      </AuthCard>
    )
  }

  return (
    <AuthCard title="Email verified" subtitle="Booking confirmations will reach you here." footer={nextLink}>
      <p className="rounded-(--field-radius) bg-pine-400/15 px-3 py-2 text-center font-medium text-pine-700">{result.data.email}</p>
    </AuthCard>
  )
}

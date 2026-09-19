import { useQuery } from '@tanstack/react-query'
import { useLocation } from 'react-router-dom'
import type { User } from '../../api/auth.ts'
import { getProfile } from '../../api/profile.ts'
import { messageFor } from '../../auth/errorMessages.ts'
import { useAuth } from '../../auth/useAuth.ts'
import { GuestNotice } from '../../components/booking/GuestNotice.tsx'
import { ProfileForm } from '../../components/profile/ProfileForm.tsx'
import { ProfileHeader } from '../../components/profile/ProfileHeader.tsx'
import { PrimaryButton } from '../../components/profile/ProfileSection.tsx'
import { SecuritySection } from '../../components/profile/SecuritySection.tsx'

/** /account/profile — rendered inside <RequireAuth role="TREKKER">. */
export function ProfilePage() {
  const auth = useAuth()
  const user = auth.status === 'authenticated' ? auth.user : null
  if (!user) return null
  return <Profile user={user} />
}

function Profile({ user }: { user: User }) {
  const { withAuth } = useAuth()
  const location = useLocation()
  const welcome = (location.state as { welcome?: boolean } | null)?.welcome === true
  const profile = useQuery({
    queryKey: ['trekker-profile', user.id],
    queryFn: () => withAuth(getProfile),
  })

  return (
    <div className="max-w-3xl space-y-8 px-4 py-8 sm:px-10 sm:py-12">
      <header>
        <h1 className="font-display text-3xl font-medium text-stone-900 sm:text-4xl">My profile</h1>
        <p className="mt-2 text-sm text-stone-600">Used for your bookings and for the guide to reach you on the trail.</p>
      </header>

      {welcome && !user.full_name && (
        <div className="rounded-(--field-radius) border border-laterite-400/40 bg-laterite-100 px-5 py-4 text-sm text-laterite-600" role="status">
          <p className="font-semibold">Welcome to Sahyātri!</p>
          <p className="mt-0.5">Add your name below so your guide knows who's coming.</p>
        </div>
      )}

      {user.guest && <GuestNotice onProfile />}

      {profile.isPending ? (
        <ProfileSkeleton />
      ) : profile.isError ? (
        <div className="border-t border-paper-300 pt-8 text-center">
          <p className="text-stone-700">{messageFor(profile.error)}</p>
          <PrimaryButton type="button" onClick={() => void profile.refetch()} className="mt-3">
            Try again
          </PrimaryButton>
        </div>
      ) : (
        <>
          <ProfileHeader user={user} profile={profile.data} />
          {/* Keyed by user so switching accounts never shows the previous draft. */}
          <ProfileForm key={user.id} user={user} profile={profile.data} />
          <SecuritySection user={user} />
        </>
      )}
    </div>
  )
}

function ProfileSkeleton() {
  return (
    <div className="space-y-5 border-t border-paper-300 pt-8" aria-busy="true" aria-label="Loading profile">
      {[56, 48, 48, 48].map((h, i) => (
        <div key={i} className="animate-pulse rounded-(--field-radius) bg-paper-200" style={{ height: h }} />
      ))}
    </div>
  )
}

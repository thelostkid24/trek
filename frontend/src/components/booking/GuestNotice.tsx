import { Link } from 'react-router-dom'
import { PROFILE_PATH } from '../../auth/useCompleteSignIn.ts'

/**
 * A guest's only way back to their trips is this browser's session. Verifying a phone or email on the profile
 * turns the guest into a normal account (docs/TRD.md §7.6).
 */
export function GuestNotice({ onProfile = false }: { onProfile?: boolean }) {
  return (
    <div role="status" className="rounded-2xl bg-amber-50 px-5 py-4 text-sm text-amber-900 ring-1 ring-amber-200">
      <p className="font-semibold">Keep access to your trip</p>
      <p className="mt-0.5">
        You booked as a guest, so this trip lives in this browser only.{' '}
        {onProfile ? (
          'Verify your mobile number or email below to open it from anywhere.'
        ) : (
          <>
            <Link to={PROFILE_PATH} className="font-semibold underline">
              Verify your mobile or email
            </Link>{' '}
            to open it from anywhere.
          </>
        )}
      </p>
    </div>
  )
}

export const GUEST_SIGN_OUT_WARNING =
  "You booked as a guest. After signing out you can't get back to your trips until you verify a mobile number or email on your profile. Sign out anyway?"

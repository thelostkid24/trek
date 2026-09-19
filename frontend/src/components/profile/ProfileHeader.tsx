import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useRef, useState, type ChangeEvent } from 'react'
import { AVATAR_TYPES, MAX_AVATAR_BYTES, removeAvatar, uploadAvatar } from '../../api/account.ts'
import type { User } from '../../api/auth.ts'
import type { CompletionItem, TrekkerProfile } from '../../api/profile.ts'
import { messageFor } from '../../auth/errorMessages.ts'
import { useAuth } from '../../auth/useAuth.ts'
import { Avatar } from '../Avatar.tsx'
import { FormError } from '../auth/AuthCard.tsx'
import { SecondaryButton } from './ProfileSection.tsx'

const ITEM_LABELS: Record<CompletionItem, string> = {
  full_name: 'Your name',
  avatar: 'Photo',
  date_of_birth: 'Date of birth',
  gender: 'Gender',
  home_city: 'Home city',
  experience_level: 'Trek experience',
  emergency_contact: 'Emergency contact',
  blood_group: 'Blood group',
  height_weight: 'Height & weight',
  diet: 'Diet',
  phone_verified: 'Verified mobile',
  email_verified: 'Verified email',
}

/** Photo and how complete the profile is. The page title and name live elsewhere. */
export function ProfileHeader({ user, profile }: { user: User; profile: TrekkerProfile }) {
  const { withAuth, updateUser } = useAuth()
  const queryClient = useQueryClient()
  const fileInput = useRef<HTMLInputElement>(null)
  const [error, setError] = useState('')

  const onUserChanged = (updated: User) => {
    updateUser(updated)
    void queryClient.invalidateQueries({ queryKey: ['trekker-profile', updated.id] })
  }
  const upload = useMutation({
    mutationFn: (file: File) => withAuth((token) => uploadAvatar(token, file)),
    onSuccess: onUserChanged,
    onError: (err) => setError(messageFor(err)),
  })
  const remove = useMutation({
    mutationFn: () => withAuth(removeAvatar),
    onSuccess: onUserChanged,
    onError: (err) => setError(messageFor(err)),
  })
  const busy = upload.isPending || remove.isPending

  function onFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    event.target.value = '' // allow re-picking the same file
    if (!file) return
    setError('')
    if (!AVATAR_TYPES.includes(file.type)) {
      setError('Please choose a JPEG or PNG photo.')
    } else if (file.size > MAX_AVATAR_BYTES) {
      setError('That photo is over 5 MB. Please choose a smaller one.')
    } else {
      upload.mutate(file)
    }
  }

  const { percent, missing } = profile.completion

  return (
    <section aria-label="Photo and profile completion" className="border-t border-paper-300 pt-8">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center">
        <div className="relative w-fit">
          <Avatar url={user.avatar_url} name={user.full_name} size="md" className="ring-paper-50!" />
          {busy && (
            <span className="absolute inset-0 flex items-center justify-center rounded-full bg-paper-50/70" role="status">
              <span className="size-5 animate-spin rounded-full border-2 border-paper-300 border-t-pine-600" />
              <span className="sr-only">Updating photo</span>
            </span>
          )}
        </div>
        <div className="min-w-0 flex-1">
          <p className="text-sm font-medium text-stone-800">Profile photo</p>
          <p className="text-xs text-stone-500">Helps your guide spot you at the meeting point. JPEG or PNG, up to 5 MB.</p>
        </div>
        <div className="flex gap-2">
          <input
            ref={fileInput}
            type="file"
            accept={AVATAR_TYPES.join(',')}
            onChange={onFile}
            className="sr-only"
            tabIndex={-1}
            aria-hidden="true"
          />
          <SecondaryButton disabled={busy} onClick={() => fileInput.current?.click()}>
            {user.avatar_url ? 'Change photo' : 'Add photo'}
          </SecondaryButton>
          {user.avatar_url && (
            <button
              type="button"
              disabled={busy}
              onClick={() => {
                setError('')
                remove.mutate()
              }}
              className="px-2 py-2 text-sm text-stone-600 hover:text-laterite-600 disabled:opacity-60"
            >
              Remove
            </button>
          )}
        </div>
      </div>
      {error && (
        <div className="mt-4">
          <FormError>{error}</FormError>
        </div>
      )}

      <div className="mt-6">
        <div className="flex items-baseline justify-between text-sm">
          <span className="text-stone-700">
            Profile <span className="font-medium text-stone-900">{percent}%</span> complete
          </span>
          {percent === 100 && <span className="text-pine-700">Ready for the trail</span>}
        </div>
        <div
          className="mt-2 h-1 overflow-hidden rounded-full bg-paper-300"
          role="progressbar"
          aria-valuenow={percent}
          aria-valuemin={0}
          aria-valuemax={100}
          aria-label="Profile completion"
        >
          <div className="h-full rounded-full bg-pine-600 transition-[width]" style={{ width: `${percent}%` }} />
        </div>
        {missing.length > 0 && (
          <p className="mt-2 text-xs text-stone-500">
            Still to add: {missing.map((item) => ITEM_LABELS[item]).join(' · ')}
          </p>
        )}
      </div>
    </section>
  )
}

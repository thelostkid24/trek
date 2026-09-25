import { useMutation } from '@tanstack/react-query'
import { updateMarketingConsent } from '../../api/account.ts'
import type { User } from '../../api/auth.ts'
import { messageFor } from '../../auth/errorMessages.ts'
import { useAuth } from '../../auth/useAuth.ts'
import { FormError } from '../auth/AuthCard.tsx'
import { ProfileSection } from './ProfileSection.tsx'

type Channel = 'email' | 'whatsapp'

/** Opt in or out of trek offers per channel (docs/TRD.md §7.15). Each toggle saves at once. */
export function CommunicationSection({ user }: { user: User }) {
  const { withAuth, updateUser } = useAuth()
  const save = useMutation({
    mutationFn: (change: Partial<Record<Channel, boolean>>) => withAuth((token) => updateMarketingConsent(token, change)),
    onSuccess: updateUser,
  })

  return (
    <ProfileSection
      id="communication"
      title="Communication preferences"
      description="New dates and trek offers. Booking updates, receipts and your guide's messages always come."
    >
      <div className="divide-y divide-paper-300">
        <Toggle
          label="Trek offers by email"
          checked={user.marketing_email}
          disabled={save.isPending}
          onChange={(email) => save.mutate({ email })}
        />
        <Toggle
          label="Trek offers on WhatsApp"
          checked={user.marketing_whatsapp}
          disabled={save.isPending}
          onChange={(whatsapp) => save.mutate({ whatsapp })}
        />
      </div>
      <FormError>{save.error ? messageFor(save.error) : ''}</FormError>
    </ProfileSection>
  )
}

function Toggle({
  label,
  checked,
  disabled,
  onChange,
}: {
  label: string
  checked: boolean
  disabled: boolean
  onChange: (checked: boolean) => void
}) {
  return (
    <label className="flex cursor-pointer items-center justify-between gap-4 py-3 first:pt-0 last:pb-0">
      <span className="text-sm text-stone-800">{label}</span>
      <input
        type="checkbox"
        role="switch"
        checked={checked}
        disabled={disabled}
        onChange={(e) => onChange(e.target.checked)}
        className="size-4 accent-brand-800"
      />
    </label>
  )
}

import { HEARD_FROM_OPTIONS, type HeardFrom, type SignupChoices } from '../../analytics/attribution.ts'
import { SelectField } from '../profile/fields.tsx'
import { TextField } from './TextField.tsx'

/**
 * "How did you hear about us?" and the two marketing consents (docs/TRD.md §7.15), for sign-up and guest
 * checkout. Everything optional; both boxes start unticked, which is what makes the consent valid.
 */
export function SignupChoicesFields({
  value,
  onChange,
}: {
  value: SignupChoices
  onChange: (next: SignupChoices) => void
}) {
  const set = (patch: Partial<SignupChoices>) => onChange({ ...value, ...patch })

  return (
    <div className="space-y-4">
      <SelectField
        label="How did you hear about us?"
        name="heard_from"
        placeholder="Choose one (optional)"
        options={HEARD_FROM_OPTIONS}
        value={value.heard_from ?? ''}
        onChange={(e) => set({ heard_from: (e.target.value || undefined) as HeardFrom | undefined })}
      />
      {value.heard_from === 'OTHER' && (
        <TextField
          label="Where did you hear about us?"
          name="heard_from_note"
          maxLength={200}
          value={value.heard_from_note ?? ''}
          onChange={(e) => set({ heard_from_note: e.target.value })}
        />
      )}
      <fieldset>
        <legend className="text-sm font-medium text-stone-800">New dates and trek offers</legend>
        <div className="mt-2 space-y-2">
          <Consent
            label="Send them by email"
            checked={value.marketing_email === true}
            onChange={(checked) => set({ marketing_email: checked })}
          />
          <Consent
            label="Send them on WhatsApp"
            checked={value.marketing_whatsapp === true}
            onChange={(checked) => set({ marketing_whatsapp: checked })}
          />
        </div>
        <p className="mt-1.5 text-xs text-stone-500">Optional. Booking messages come either way; change this any time in your profile.</p>
      </fieldset>
    </div>
  )
}

function Consent({ label, checked, onChange }: { label: string; checked: boolean; onChange: (checked: boolean) => void }) {
  return (
    <label className="flex cursor-pointer items-center gap-2.5 text-sm text-stone-700">
      <input type="checkbox" checked={checked} onChange={(e) => onChange(e.target.checked)} className="size-4 accent-brand-800" />
      {label}
    </label>
  )
}

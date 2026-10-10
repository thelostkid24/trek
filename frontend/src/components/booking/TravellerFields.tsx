import type { Gender } from '../../api/profile.ts'
import type { OfferedAddon } from '../../lib/addons.ts'
import { rupees } from '../../lib/format.ts'
import { GENDERS, type TravellerDraft } from '../../lib/travellers.ts'
import { TextField } from '../auth/TextField.tsx'
import { SelectField } from '../profile/fields.tsx'

// One traveller's form (who they are, then their add-ons), shared by checkout and the booking page.


/** One traveller: who they are, then their add-ons. Our insurance is compulsory where offered. */
export function TravellerFields({
  index: i,
  traveller: t,
  update,
  errors,
  startDate,
  offered,
  addonsLocked,
}: {
  index: number
  traveller: TravellerDraft
  update: (patch: Partial<TravellerDraft>) => void
  errors: Record<string, string>
  startDate: string
  offered: OfferedAddon[]
  addonsLocked: boolean
}) {
  return (
    <>
      <p className="text-sm font-semibold text-stone-800">Traveller {i + 1}</p>
      <div className="mt-3 grid gap-4 sm:grid-cols-2">
        <TextField label="Full name" name={`travellers-${i}-full_name`} required maxLength={100} value={t.full_name}
          onChange={(e) => update({ full_name: e.target.value })} error={errors[`travellers[${i}].full_name`]} />
        <TextField label="Mobile (optional)" name={`travellers-${i}-phone`} type="tel" placeholder="+919876543210" value={t.phone}
          onChange={(e) => update({ phone: e.target.value })} error={errors[`travellers[${i}].phone`]} />
        <TextField label="Date of birth" name={`travellers-${i}-date_of_birth`} type="date" required max={startDate}
          value={t.date_of_birth} onChange={(e) => update({ date_of_birth: e.target.value })}
          error={errors[`travellers[${i}].date_of_birth`]} />
        <SelectField label="Gender" name={`travellers-${i}-gender`} required value={t.gender}
          onChange={(e) => update({ gender: e.target.value as Gender | '' })} options={GENDERS}
          error={errors[`travellers[${i}].gender`]} hint="Helps your guide plan tents and rooms." />
      </div>

      {offered.length > 0 && (
        <div className="mt-4 rounded-xl bg-paper-100 p-4">
          <AddonFields index={i} traveller={t} update={update} errors={errors} offered={offered} locked={addonsLocked} />
        </div>
      )}
    </>
  )
}

/**
 * One traveller's add-ons. Our insurance is compulsory wherever the trek offers it, so it's always ticked and can't be
 * unticked; a paid booking from before that shows the policy it was paid with.
 */
export function AddonFields({
  index: i,
  traveller: t,
  update,
  errors,
  offered,
  locked,
}: {
  index: number
  traveller: TravellerDraft
  update: (patch: Partial<TravellerDraft>) => void
  errors: Record<string, string>
  offered: OfferedAddon[]
  locked: boolean
}) {
  const insurance = offered.find((a) => a.key === 'insurance')
  const extras = offered.filter((a) => a.key !== 'insurance')
  return (
    <fieldset disabled={locked}>
      <legend className="sr-only">Add-ons for traveller {i + 1}</legend>
      {insurance && (
        <AddonCheck
          name={`travellers-${i}-insurance`}
          checked={t.insurance}
          onChange={() => {}}
          disabled
          label={insurance.label}
          price={insurance.price}
          note={!t.insurance && t.insurance_id ? `Own policy ${t.insurance_id}` : 'Required for everyone on this trek.'}
          error={errors[`travellers[${i}].insurance`]}
        />
      )}
      {extras.map((a) => (
        <div key={a.key} className={insurance || a !== extras[0] ? 'mt-3 border-t border-paper-300 pt-3' : ''}>
          <AddonCheck
            name={`travellers-${i}-${a.key}`}
            checked={a.key === 'offloading' ? t.offloading : t.transport}
            onChange={(on) => update(a.key === 'offloading' ? { offloading: on } : { transport: on })}
            label={a.label}
            price={a.price}
            note={a.note}
            error={errors[`travellers[${i}].${a.key}`]}
          />
        </div>
      ))}
      {locked && <p className="mt-3 text-xs text-stone-500">Paid add-ons can't be changed.</p>}
    </fieldset>
  )
}

function AddonCheck({ name, checked, onChange, disabled = false, label, price, note, error }: {
  name: string
  checked: boolean
  onChange: (on: boolean) => void
  disabled?: boolean
  label: string
  price: number
  note: string
  error?: string
}) {
  return (
    <label htmlFor={name} className={`flex items-start gap-3 ${disabled ? '' : 'cursor-pointer'}`}>
      <input id={name} name={name} type="checkbox" checked={checked} disabled={disabled}
        onChange={(e) => onChange(e.target.checked)} className="mt-1 size-4 accent-pine-600 disabled:opacity-100" />
      <span className="min-w-0 flex-1">
        <span className="flex justify-between gap-3 text-sm font-medium text-stone-900">
          {label}
          <span className="tabular-nums">{rupees(price)}</span>
        </span>
        <span className="block text-xs text-stone-500">{note}</span>
        {error && <span className="block text-xs text-laterite-600">{error}</span>}
      </span>
    </label>
  )
}

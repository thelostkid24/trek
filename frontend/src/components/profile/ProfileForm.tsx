import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import type { User } from '../../api/auth.ts'
import {
  updateProfile,
  type BloodGroup,
  type Diet,
  type ExperienceLevel,
  type Gender,
  type TrekkerProfile,
  type TrekkerProfileUpdate,
} from '../../api/profile.ts'
import { fieldErrors, messageFor } from '../../auth/errorMessages.ts'
import { useAuth } from '../../auth/useAuth.ts'
import { INDIAN_MOBILE } from '../../auth/validation.ts'
import { formatPhone } from '../../lib/format.ts'
import { FormError } from '../auth/AuthCard.tsx'
import { TextField } from '../auth/TextField.tsx'
import { SelectField, TextAreaField } from './fields.tsx'
import { Badge, PrimaryButton, ProfileSection } from './ProfileSection.tsx'

const GENDERS: { value: Gender; label: string }[] = [
  { value: 'FEMALE', label: 'Female' },
  { value: 'MALE', label: 'Male' },
  { value: 'NON_BINARY', label: 'Non-binary' },
  { value: 'PREFER_NOT_TO_SAY', label: 'Prefer not to say' },
]

const LEVELS: { value: ExperienceLevel; label: string; body: string }[] = [
  { value: 'BEGINNER', label: 'Beginner', body: 'New to trekking, or a few easy trails.' },
  { value: 'INTERMEDIATE', label: 'Intermediate', body: 'A few multi-day treks behind you.' },
  { value: 'EXPERIENCED', label: 'Experienced', body: 'Regular high-altitude or technical routes.' },
]

const BLOOD_GROUPS: BloodGroup[] = ['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-']

const DIETS: { value: Diet; label: string }[] = [
  { value: 'VEGETARIAN', label: 'Vegetarian' },
  { value: 'EGGETARIAN', label: 'Vegetarian + eggs' },
  { value: 'NON_VEGETARIAN', label: 'Non-vegetarian' },
  { value: 'VEGAN', label: 'Vegan' },
  { value: 'JAIN', label: 'Jain' },
]

const SHOE_SIZES = Array.from({ length: 15 }, (_, i) => ({ value: String(i + 1), label: `UK ${i + 1}` }))

/** Server ranges (docs/TRD.md §7.3); checked here too so mistakes show before saving. */
const RANGES = {
  height_cm: { min: 100, max: 250, message: 'Enter your height in cm (100–250)' },
  weight_kg: { min: 25, max: 250, message: 'Enter your weight in kg (25–250)' },
  highest_altitude_m: { min: 0, max: 8849, message: 'Enter an altitude in metres (0–8849)' },
} as const

/** Every field as a string, the way inputs hold them. Empty string = not set. */
type Draft = {
  full_name: string
  date_of_birth: string
  gender: string
  home_city: string
  experience_level: string
  highest_altitude_m: string
  bio: string
  emergency_name: string
  emergency_relation: string
  /** 10 digits, without +91. */
  emergency_phone: string
  height_cm: string
  weight_kg: string
  blood_group: string
  allergies: string
  medical_notes: string
  diet: string
  shoe_size_uk: string
}

const str = (n: number | null) => (n === null ? '' : String(n))

function toDraft(p: TrekkerProfile): Draft {
  return {
    full_name: p.full_name ?? '',
    date_of_birth: p.date_of_birth ?? '',
    gender: p.gender ?? '',
    home_city: p.home_city ?? '',
    experience_level: p.experience_level ?? '',
    highest_altitude_m: str(p.highest_altitude_m),
    bio: p.bio ?? '',
    emergency_name: p.emergency_contact?.name ?? '',
    emergency_relation: p.emergency_contact?.relation ?? '',
    emergency_phone: p.emergency_contact?.phone.replace(/^\+91/, '') ?? '',
    height_cm: str(p.height_cm),
    weight_kg: str(p.weight_kg),
    blood_group: p.blood_group ?? '',
    allergies: p.allergies ?? '',
    medical_notes: p.medical_notes ?? '',
    diet: p.diet ?? '',
    shoe_size_uk: str(p.shoe_size_uk),
  }
}

const orNull = <T extends string>(value: string) => (value.trim() ? (value.trim() as T) : null)
const intOrNull = (value: string) => (value.trim() ? Number(value) : null)

function toUpdate(d: Draft): TrekkerProfileUpdate {
  const hasContact = [d.emergency_name, d.emergency_relation, d.emergency_phone].some((v) => v.trim())
  return {
    full_name: d.full_name.trim(),
    date_of_birth: orNull(d.date_of_birth),
    gender: orNull<Gender>(d.gender),
    home_city: orNull(d.home_city),
    experience_level: orNull<ExperienceLevel>(d.experience_level),
    highest_altitude_m: intOrNull(d.highest_altitude_m),
    bio: orNull(d.bio),
    emergency_contact: hasContact
      ? { name: d.emergency_name.trim(), relation: d.emergency_relation.trim(), phone: `+91${d.emergency_phone}` }
      : null,
    height_cm: intOrNull(d.height_cm),
    weight_kg: intOrNull(d.weight_kg),
    blood_group: orNull<BloodGroup>(d.blood_group),
    allergies: orNull(d.allergies),
    medical_notes: orNull(d.medical_notes),
    diet: orNull<Diet>(d.diet),
    shoe_size_uk: intOrNull(d.shoe_size_uk),
  }
}

/** Latest date of birth that makes someone 18 today (server checks the same rule on IST dates). */
function latestBirthDate(): string {
  const d = new Date()
  d.setFullYear(d.getFullYear() - 18)
  return d.toLocaleDateString('en-CA') // YYYY-MM-DD in local time
}

function validate(d: Draft, ownPhone: string | null): Record<string, string> {
  const problems: Record<string, string> = {}
  if (!d.full_name.trim()) problems.full_name = 'Tell us your name'
  if (d.date_of_birth && d.date_of_birth > latestBirthDate()) {
    problems.date_of_birth = 'You must be at least 18 to book treks'
  }
  const hasContact = [d.emergency_name, d.emergency_relation, d.emergency_phone].some((v) => v.trim())
  if (hasContact) {
    if (!d.emergency_name.trim()) problems['emergency_contact.name'] = 'Add their name'
    if (!d.emergency_relation.trim()) problems['emergency_contact.relation'] = 'How are they related to you?'
    if (!INDIAN_MOBILE.test(d.emergency_phone)) {
      problems['emergency_contact.phone'] = 'Enter a 10-digit Indian mobile number'
    } else if (`+91${d.emergency_phone}` === ownPhone) {
      problems['emergency_contact.phone'] = "Use someone else's number"
    }
  }
  for (const [key, range] of Object.entries(RANGES)) {
    const value = d[key as keyof typeof RANGES].trim()
    if (value && !(/^\d+$/.test(value) && Number(value) >= range.min && Number(value) <= range.max)) {
      problems[key] = range.message
    }
  }
  return problems
}

const digitsOnly = (value: string, max: number) => value.replace(/\D/g, '').slice(0, max)

/**
 * Everything on /api/trekker/profile, edited together and saved with one PUT since the endpoint replaces the
 * whole profile. Phone and email are shown for reference; they change through Sign-in & security.
 */
export function ProfileForm({ user, profile }: { user: User; profile: TrekkerProfile }) {
  const { withAuth, updateUser } = useAuth()
  const queryClient = useQueryClient()
  const [saved, setSaved] = useState(() => toDraft(profile))
  const [draft, setDraft] = useState(saved)
  const [fields, setFields] = useState<Record<string, string>>({})
  const [error, setError] = useState('')
  const [justSaved, setJustSaved] = useState(false)

  const dirty = JSON.stringify(draft) !== JSON.stringify(saved)

  const save = useMutation({
    mutationFn: (body: TrekkerProfileUpdate) => withAuth((token) => updateProfile(token, body)),
    onSuccess: (updated) => {
      queryClient.setQueryData(['trekker-profile', user.id], updated)
      updateUser({ ...user, full_name: updated.full_name })
      const next = toDraft(updated)
      setSaved(next)
      setDraft(next)
      setJustSaved(true)
    },
    onError: (err) => {
      setError(messageFor(err))
      setFields(fieldErrors(err))
    },
  })

  const update = (key: keyof Draft, value: string) => {
    setDraft((d) => ({ ...d, [key]: value }))
    setJustSaved(false)
  }
  const set = (key: keyof Draft) => (event: { target: { value: string } }) => update(key, event.target.value)
  const setDigits = (key: keyof Draft, max: number) => (event: { target: { value: string } }) =>
    update(key, digitsOnly(event.target.value, max))

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    setError('')
    const problems = validate(draft, user.phone)
    setFields(problems)
    if (Object.keys(problems).length > 0) {
      setError('Please check the highlighted fields.')
      return
    }
    save.mutate(toUpdate(draft))
  }

  function discard() {
    setDraft(saved)
    setFields({})
    setError('')
  }

  const hasContact = Boolean(profile.emergency_contact)
  const changeHint = (
    <>
      Used to sign in, so it changes under{' '}
      <a href="#security" className="text-pine-700 underline hover:text-pine-600">
        Sign-in &amp; security
      </a>
      .
    </>
  )

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-10">
      <ProfileSection title="Personal details" description="Treks are for adults, so your date of birth must make you 18 or over.">
        <div className="grid gap-5 sm:grid-cols-2">
          <div className="sm:col-span-2">
            <TextField
              label="Full name"
              name="full_name"
              autoComplete="name"
              value={draft.full_name}
              onChange={set('full_name')}
              error={fields.full_name}
              maxLength={100}
            />
          </div>
          <TextField
            label="Phone number"
            name="account_phone"
            value={user.phone ? formatPhone(user.phone) : 'Not added yet'}
            disabled
            hint={changeHint}
          />
          <TextField
            label="Email"
            name="account_email"
            value={user.email ?? 'Not added yet'}
            disabled
            hint={changeHint}
          />
          <TextField
            label="Date of birth"
            name="date_of_birth"
            type="date"
            autoComplete="bday"
            max={latestBirthDate()}
            value={draft.date_of_birth}
            onChange={set('date_of_birth')}
            error={fields.date_of_birth}
          />
          <SelectField
            label="Gender"
            name="gender"
            options={GENDERS}
            value={draft.gender}
            onChange={set('gender')}
            error={fields.gender}
          />
          <div className="sm:col-span-2">
            <TextField
              label="Home city"
              name="home_city"
              autoComplete="address-level2"
              placeholder="Pune"
              value={draft.home_city}
              onChange={set('home_city')}
              error={fields.home_city}
              maxLength={100}
            />
          </div>
        </div>
      </ProfileSection>

      <ProfileSection
        title="Emergency contact"
        description="One person we call if we can't reach you on the mountain."
        badge={hasContact ? <Badge tone="good">Added</Badge> : <Badge tone="warn">Needed before you trek</Badge>}
      >
        <div className="grid gap-5 sm:grid-cols-[1.2fr_0.9fr_1.1fr]">
          <TextField
            label="Name"
            name="emergency_contact.name"
            value={draft.emergency_name}
            onChange={set('emergency_name')}
            error={fields['emergency_contact.name']}
            maxLength={100}
          />
          <TextField
            label="Relationship"
            name="emergency_contact.relation"
            placeholder="Mother, partner…"
            value={draft.emergency_relation}
            onChange={set('emergency_relation')}
            error={fields['emergency_contact.relation']}
            maxLength={50}
          />
          <TextField
            label="Phone"
            name="emergency_contact.phone"
            type="tel"
            inputMode="numeric"
            prefix="+91"
            placeholder="98765 43210"
            value={draft.emergency_phone}
            onChange={setDigits('emergency_phone', 10)}
            error={fields['emergency_contact.phone']}
          />
        </div>
        {hasContact && (
          <button
            type="button"
            onClick={() => setDraft((d) => ({ ...d, emergency_name: '', emergency_relation: '', emergency_phone: '' }))}
            className="mt-3 text-sm text-stone-600 hover:text-laterite-600"
          >
            Remove contact
          </button>
        )}
      </ProfileSection>

      <ProfileSection title="Trekking experience" description="Helps your guide pace the group and check you're ready for the route.">
        <fieldset>
          <legend className="text-sm font-medium text-stone-800">How much have you trekked?</legend>
          <div className="mt-2 grid gap-2 sm:grid-cols-3">
            {LEVELS.map((level) => {
              const checked = draft.experience_level === level.value
              return (
                <label
                  key={level.value}
                  className={`cursor-pointer rounded-(--field-radius) border p-3 transition has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-(--field-ring) ${
                    checked ? 'border-pine-600 bg-paper-50 shadow-[inset_3px_0_0_var(--color-pine-600)]' : 'border-paper-300 bg-paper-50/60 hover:border-pine-400'
                  }`}
                >
                  <input
                    type="radio"
                    name="experience_level"
                    value={level.value}
                    checked={checked}
                    onChange={set('experience_level')}
                    className="sr-only"
                  />
                  <span className={`block text-sm font-medium ${checked ? 'text-pine-700' : 'text-stone-900'}`}>
                    {level.label}
                  </span>
                  <span className="mt-0.5 block text-xs text-stone-600">{level.body}</span>
                </label>
              )
            })}
          </div>
          {fields.experience_level && <p className="mt-1 text-sm text-laterite-600">{fields.experience_level}</p>}
        </fieldset>
        <div className="mt-5 grid gap-5 sm:grid-cols-2">
          <TextField
            label="Highest altitude you've reached"
            name="highest_altitude_m"
            inputMode="numeric"
            suffix="m"
            placeholder="3800"
            value={draft.highest_altitude_m}
            onChange={setDigits('highest_altitude_m', 4)}
            error={fields.highest_altitude_m}
            hint="Summit or highest camp. Leave empty if you're not sure."
          />
        </div>
        <div className="mt-5">
          <TextAreaField
            label="About your trekking"
            name="bio"
            placeholder="Treks you've done, fitness routine, what you're hoping to try next…"
            maxLength={500}
            value={draft.bio}
            onChange={set('bio')}
            error={fields.bio}
            hint="Optional"
          />
        </div>
      </ProfileSection>

      <ProfileSection
        title="Health & fitness"
        description={
          <>
            <span className="font-medium text-stone-800">Private to you.</span> Used to check you're fit for altitude and
            to help quickly if something goes wrong.
          </>
        }
      >
        <div className="grid gap-5 sm:grid-cols-3">
          <TextField
            label="Height"
            name="height_cm"
            inputMode="numeric"
            suffix="cm"
            value={draft.height_cm}
            onChange={setDigits('height_cm', 3)}
            error={fields.height_cm}
          />
          <TextField
            label="Weight"
            name="weight_kg"
            inputMode="numeric"
            suffix="kg"
            value={draft.weight_kg}
            onChange={setDigits('weight_kg', 3)}
            error={fields.weight_kg}
          />
          <SelectField
            label="Blood group"
            name="blood_group"
            placeholder="Not sure"
            options={BLOOD_GROUPS.map((g) => ({ value: g, label: g }))}
            value={draft.blood_group}
            onChange={set('blood_group')}
            error={fields.blood_group}
          />
          <div className="sm:col-span-3">
            <TextField
              label="Allergies"
              name="allergies"
              placeholder="Food, medicine, insect stings… or leave empty"
              value={draft.allergies}
              onChange={set('allergies')}
              error={fields.allergies}
              maxLength={300}
            />
          </div>
          <div className="sm:col-span-3">
            <TextAreaField
              label="Medical conditions & medication"
              name="medical_notes"
              placeholder="Asthma, blood pressure, recent injuries, regular medication…"
              maxLength={1000}
              value={draft.medical_notes}
              onChange={set('medical_notes')}
              error={fields.medical_notes}
              hint="Optional"
            />
          </div>
        </div>
      </ProfileSection>

      <ProfileSection title="Food & gear" description="So the kitchen cooks for you and rental boots fit when you arrive.">
        <div className="grid gap-5 sm:grid-cols-2">
          <SelectField
            label="Diet"
            name="diet"
            options={DIETS}
            value={draft.diet}
            onChange={set('diet')}
            error={fields.diet}
          />
          <SelectField
            label="Shoe size"
            name="shoe_size_uk"
            placeholder="Select UK size…"
            options={SHOE_SIZES}
            value={draft.shoe_size_uk}
            onChange={set('shoe_size_uk')}
            error={fields.shoe_size_uk}
            hint="Only needed if you rent trekking shoes."
          />
        </div>
      </ProfileSection>

      {/* Sticky so the save action stays reachable while scrolling the long form. */}
      <div className="sticky bottom-0 z-10 -mx-4 flex flex-col gap-3 border-t border-paper-300 bg-paper-100/95 px-4 py-4 backdrop-blur sm:mx-0 sm:flex-row-reverse sm:items-center sm:justify-end sm:px-0">
        <div className="min-w-0 text-sm sm:ml-3" aria-live="polite">
          {error ? (
            <FormError>{error}</FormError>
          ) : dirty ? (
            <span className="text-stone-700">You have unsaved changes.</span>
          ) : justSaved ? (
            <span className="text-pine-700">Profile saved.</span>
          ) : (
            <span className="text-stone-500">All changes saved.</span>
          )}
        </div>
        <div className="flex shrink-0 items-center gap-2">
          <PrimaryButton pending={save.isPending} disabled={!dirty}>
            Save changes
          </PrimaryButton>
          {dirty && (
            <button
              type="button"
              onClick={discard}
              disabled={save.isPending}
              className="px-3 py-2.5 text-sm text-stone-600 hover:text-stone-900"
            >
              Discard
            </button>
          )}
        </div>
      </div>
    </form>
  )
}

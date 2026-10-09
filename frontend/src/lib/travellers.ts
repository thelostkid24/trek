import type { TravellerInput } from '../api/bookings.ts'
import type { Gender } from '../api/profile.ts'
import type { OfferedAddon } from './addons.ts'

// A traveller as typed into checkout or the booking page, and what goes to the API.

export const GENDERS: { value: Gender; label: string }[] = [
  { value: 'FEMALE', label: 'Female' },
  { value: 'MALE', label: 'Male' },
  { value: 'NON_BINARY', label: 'Non-binary' },
  { value: 'PREFER_NOT_TO_SAY', label: 'Prefer not to say' },
]

export type TravellerDraft = {
  full_name: string
  phone: string
  date_of_birth: string
  gender: Gender | ''
  insurance: boolean
  insurance_id: string
  offloading: boolean
  transport: boolean
}

/** A blank traveller. Our insurance starts ticked where it's offered. */
export function blankTraveller(offered: OfferedAddon[]): TravellerDraft {
  return {
    full_name: '',
    phone: '',
    date_of_birth: '',
    gender: '',
    insurance: offered.some((a) => a.key === 'insurance'),
    insurance_id: '',
    offloading: false,
    transport: false,
  }
}

/** What the server would refuse anyway, caught before anything is held. Ages are left to the server. */
export function travellerErrors(drafts: TravellerDraft[], offered: OfferedAddon[]): Record<string, string> {
  const insuranceOffered = offered.some((a) => a.key === 'insurance')
  const found: Record<string, string> = {}
  drafts.forEach((t, i) => {
    const key = `travellers[${i}].`
    if (!t.full_name.trim()) found[`${key}full_name`] = 'Enter their full name'
    if (!t.date_of_birth) found[`${key}date_of_birth`] = 'Enter their date of birth'
    if (!t.gender) found[`${key}gender`] = 'Choose one'
    if (insuranceOffered && !t.insurance && !t.insurance_id.trim()) found[`${key}insurance_id`] = 'Enter your policy ID, or tick our insurance'
  })
  return found
}

/** Each offered add-on with how many travellers take it, for the price summary. */
export function addonLines(drafts: TravellerDraft[], offered: OfferedAddon[]) {
  return offered.map((a) => ({ ...a, count: drafts.filter((t) => t[a.key]).length })).filter((a) => a.count > 0)
}

export function toInput(t: TravellerDraft): TravellerInput {
  return {
    full_name: t.full_name.trim(),
    phone: t.phone.trim() === '' ? null : t.phone.trim(),
    date_of_birth: t.date_of_birth,
    gender: t.gender as Gender,
    insurance: t.insurance,
    insurance_id: t.insurance ? null : t.insurance_id.trim() || null,
    offloading: t.offloading,
    transport: t.transport,
  }
}

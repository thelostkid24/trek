import { apiFetch } from './client'

// Contract: docs/TRD.md §7.3. Call through `withAuth` from useAuth().

export type Gender = 'FEMALE' | 'MALE' | 'NON_BINARY' | 'PREFER_NOT_TO_SAY'
export type ExperienceLevel = 'BEGINNER' | 'INTERMEDIATE' | 'EXPERIENCED'
export type BloodGroup = 'A+' | 'A-' | 'B+' | 'B-' | 'AB+' | 'AB-' | 'O+' | 'O-'
export type Diet = 'VEGETARIAN' | 'EGGETARIAN' | 'NON_VEGETARIAN' | 'VEGAN' | 'JAIN'

export type EmergencyContact = { name: string; relation: string; phone: string }

/** Keys the server reports in `completion.missing`, in display order. */
export type CompletionItem =
  | 'full_name'
  | 'avatar'
  | 'date_of_birth'
  | 'gender'
  | 'home_city'
  | 'experience_level'
  | 'emergency_contact'
  | 'blood_group'
  | 'height_weight'
  | 'diet'
  | 'phone_verified'
  | 'email_verified'

export type TrekkerProfile = {
  full_name: string | null
  avatar_url: string | null
  date_of_birth: string | null // ISO date
  gender: Gender | null
  home_city: string | null
  experience_level: ExperienceLevel | null
  highest_altitude_m: number | null // 0–8849
  bio: string | null
  emergency_contact: EmergencyContact | null
  height_cm: number | null // 100–250
  weight_kg: number | null // 25–250
  blood_group: BloodGroup | null
  allergies: string | null
  medical_notes: string | null
  diet: Diet | null
  shoe_size_uk: number | null // 1–15, for gear rental
  completion: { percent: number; missing: CompletionItem[] }
  updated_at: string | null
}

/** PUT body — replaces the whole profile; null clears a field. */
export type TrekkerProfileUpdate = Omit<TrekkerProfile, 'avatar_url' | 'completion' | 'updated_at' | 'full_name'> & {
  full_name: string
}

export const getProfile = (token: string) => apiFetch<TrekkerProfile>('/api/trekker/profile', { token })

export const updateProfile = (token: string, body: TrekkerProfileUpdate) =>
  apiFetch<TrekkerProfile>('/api/trekker/profile', { method: 'PUT', token, body })

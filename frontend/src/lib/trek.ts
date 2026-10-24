import { DIFFICULTY_LABEL, type GuideCredentials, type TrackDetail } from '../api/catalog.ts'
import { feet } from './format.ts'

/** States and union territories a trek can be in (the admin trek form's "State" options). */
export const INDIAN_STATES = [
  'Andaman and Nicobar Islands', 'Andhra Pradesh', 'Arunachal Pradesh', 'Assam', 'Bihar', 'Chandigarh', 'Chhattisgarh',
  'Dadra and Nagar Haveli and Daman and Diu', 'Delhi', 'Goa', 'Gujarat', 'Haryana', 'Himachal Pradesh', 'Jammu and Kashmir',
  'Jharkhand', 'Karnataka', 'Kerala', 'Ladakh', 'Lakshadweep', 'Madhya Pradesh', 'Maharashtra', 'Manipur', 'Meghalaya',
  'Mizoram', 'Nagaland', 'Odisha', 'Puducherry', 'Punjab', 'Rajasthan', 'Sikkim', 'Tamil Nadu', 'Telangana', 'Tripura',
  'Uttar Pradesh', 'Uttarakhand', 'West Bengal',
]

/** "6 days from Dehradun · summit 12,500 ft · Moderate" — the line under a trek's name. */
export function trekTagline(track: TrackDetail): string {
  const days = `${track.duration_days} ${track.duration_days === 1 ? 'day' : 'days'} from ${track.meeting_point}`
  return [days, track.max_altitude_m && `summit ${feet(track.max_altitude_m)}`, DIFFICULTY_LABEL[track.difficulty]]
    .filter(Boolean)
    .join(' · ')
}

/** True while an admin-verified certificate is still valid (docs/TRD.md §7.21.3); worked out by the server. */
export function isCertified(g: GuideCredentials) {
  return g.certified
}

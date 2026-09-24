import { DIFFICULTY_LABEL, type TrackDetail } from '../api/catalog.ts'
import { feet } from './format.ts'

/** "6 days from Dehradun · summit 12,500 ft · Moderate" — the line under a trek's name. */
export function trekTagline(track: TrackDetail): string {
  const days = `${track.duration_days} ${track.duration_days === 1 ? 'day' : 'days'} from ${track.meeting_point}`
  return [days, track.max_altitude_m && `summit ${feet(track.max_altitude_m)}`, DIFFICULTY_LABEL[track.difficulty]]
    .filter(Boolean)
    .join(' · ')
}

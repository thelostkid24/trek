import { DIFFICULTY_LABEL, type Difficulty } from '../../api/catalog.ts'

const DIFFICULTY_TONE: Record<Difficulty, string> = {
  EASY: 'bg-brand-100 text-brand-800',
  EASY_MODERATE: 'bg-lime-100 text-lime-800',
  MODERATE: 'bg-amber-100 text-amber-800',
  CHALLENGING: 'bg-laterite-100 text-laterite-600',
}

export function DifficultyPill({ difficulty }: { difficulty: Difficulty }) {
  return (
    <span className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${DIFFICULTY_TONE[difficulty]}`}>
      {DIFFICULTY_LABEL[difficulty]}
    </span>
  )
}

/**
 * The batch as ten dark-or-light bars, dark = taken, with "6 of 10 filled · 4 open". The loudest thing on a
 * departure: a trekker sees at a glance which date needs people.
 */
export function FillBar({ size, left, bookable = true }: { size: number; left: number; bookable?: boolean }) {
  const taken = size - left
  return (
    <div aria-label={`${taken} of ${size} seats filled, ${left} open`}>
      <div className="flex gap-1.5" aria-hidden="true">
        {Array.from({ length: size }, (_, i) => (
          <span key={i} className={`h-2.5 flex-1 rounded-full ${i < taken ? 'bg-brand-900' : 'bg-paper-200'}`} />
        ))}
      </div>
      <p className={`mt-2 text-sm font-medium ${left === 0 ? 'text-stone-500' : bookable && left <= 2 ? 'text-laterite-600' : 'text-stone-800'}`}>
        {left === 0 ? `${size} of ${size} filled · batch full` : `${taken} of ${size} filled · ${left} open`}
      </p>
    </div>
  )
}

/** One bar per seat in the batch; filled bars are taken. */
export function SeatMeter({ size, left }: { size: number; left: number }) {
  const taken = size - left
  const full = left === 0
  return (
    <div aria-label={`${taken} of ${size} seats booked`}>
      <div className="flex gap-1">
        {Array.from({ length: size }, (_, i) => (
          <span key={i} className={`h-1.5 flex-1 rounded-full ${i < taken ? 'bg-brand-600' : 'bg-stone-200'}`} />
        ))}
      </div>
      <p
        className={`mt-2 text-xs font-medium ${full ? 'text-stone-500' : left <= 2 ? 'text-laterite-600' : 'text-brand-700'}`}
      >
        {full ? 'Batch full' : `${taken} of ${size} seats filled · ${left} open`}
      </p>
    </div>
  )
}

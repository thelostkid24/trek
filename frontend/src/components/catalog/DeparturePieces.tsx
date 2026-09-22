import { DIFFICULTY_LABEL, type Difficulty } from '../../api/catalog.ts'

const DIFFICULTY_TONE: Record<Difficulty, string> = {
  EASY: 'bg-brand-100 text-brand-800',
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

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

/** "Batch full" once no seats are left; nothing otherwise, since we don't show how many are open. */
export function FillBar({ left }: { left: number }) {
  return left === 0 ? <p className="text-sm font-medium text-stone-500">Batch full</p> : null
}

/** The compact "Batch full" line on date cards; nothing while seats are open. */
export function SeatMeter({ left }: { left: number }) {
  return left === 0 ? <p className="text-xs font-medium text-stone-500">Batch full</p> : null
}

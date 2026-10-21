import { REVIEW_TAGS, SUB_RATINGS, type ReviewTag, type SubRatings } from '../../api/reviews.ts'

/** Shared by the booking review and the /r/{slug} form (docs/TRD.md §7.21.5). */

export function StarPicker({
  value,
  onChange,
  label,
  size = 'lg',
}: {
  value: number
  onChange: (n: number) => void
  label: string
  size?: 'lg' | 'sm'
}) {
  return (
    <div role="radiogroup" aria-label={label} className="flex gap-1">
      {[1, 2, 3, 4, 5].map((n) => (
        <button
          key={n}
          type="button"
          role="radio"
          aria-checked={value === n}
          aria-label={`${n} out of 5`}
          onClick={() => onChange(value === n && size === 'sm' ? 0 : n)}
          className={`leading-none ${size === 'lg' ? 'text-3xl' : 'text-xl'} ${
            n <= value ? 'text-laterite-600' : 'text-paper-300 hover:text-laterite-400'
          }`}
        >
          ★
        </button>
      ))}
    </div>
  )
}

/** The four optional sub-ratings; tapping the picked star again clears it. */
export function SubRatingFields({ value, onChange }: { value: SubRatings; onChange: (v: SubRatings) => void }) {
  return (
    <fieldset>
      <legend className="text-sm font-medium text-stone-800">More detail (optional)</legend>
      <div className="mt-2 grid gap-2">
        {SUB_RATINGS.map((s) => (
          <div key={s.field} className="flex items-center justify-between gap-3">
            <span className="text-sm text-stone-700">{s.label}</span>
            <StarPicker
              size="sm"
              label={s.label}
              value={value[s.field] ?? 0}
              onChange={(n) => onChange({ ...value, [s.field]: n || null })}
            />
          </div>
        ))}
      </div>
    </fieldset>
  )
}

export function TagChips({ value, onChange }: { value: ReviewTag[]; onChange: (v: ReviewTag[]) => void }) {
  return (
    <fieldset>
      <legend className="text-sm font-medium text-stone-800">What stood out?</legend>
      <div className="mt-2 flex flex-wrap gap-2">
        {REVIEW_TAGS.map((t) => {
          const on = value.includes(t.code)
          return (
            <button
              key={t.code}
              type="button"
              aria-pressed={on}
              onClick={() => onChange(on ? value.filter((c) => c !== t.code) : [...value, t.code])}
              className={`rounded-full px-3 py-1.5 text-sm ring-1 ${
                on ? 'bg-pine-700 text-white ring-pine-700' : 'bg-white text-stone-700 ring-paper-300 hover:ring-stone-400'
              }`}
            >
              {t.label}
            </button>
          )
        })}
      </div>
    </fieldset>
  )
}

export function Stars({ rating }: { rating: number }) {
  return (
    <span className="tracking-wider text-laterite-600" aria-label={`${rating} out of 5`}>
      {'★'.repeat(rating)}
      <span className="text-paper-300">{'★'.repeat(5 - rating)}</span>
    </span>
  )
}

import { useQuery } from '@tanstack/react-query'
import { listGuideAvailability } from '../../api/availability.ts'
import { useAuth } from '../../auth/useAuth.ts'
import { shortRange } from '../../lib/format.ts'

/**
 * "Free guides on these dates" beside the admin date pickers (docs/TRD.md §7.21.7): guides whose marked availability
 * shares a day with [from, to], fully covering ones first. `onPick` makes each name pick that guide.
 */
export function FreeGuidesHint({ from, to, onPick }: { from: string; to: string; onPick?: (guideId: string) => void }) {
  const { withAuth } = useAuth()
  const ready = Boolean(from && to && to >= from)
  const free = useQuery({
    queryKey: ['admin-guide-availability', from, to],
    queryFn: () => withAuth((token) => listGuideAvailability(token, from, to)),
    enabled: ready,
  })
  if (!ready || !free.data) return null
  const items = [...free.data.items].sort(
    (a, b) => Number(b.start_date <= from && b.end_date >= to) - Number(a.start_date <= from && a.end_date >= to),
  )

  return (
    <p className="text-xs text-stone-600">
      <span className="font-medium text-stone-700">Free guides on these dates: </span>
      {items.length === 0
        ? 'nobody has marked these days.'
        : items.map((a, i) => {
            const label = `${a.guide_name ?? 'Guide'} (${shortRange(a.start_date, a.end_date)}${a.note ? `, ${a.note}` : ''})`
            return (
              <span key={a.id}>
                {i > 0 && ' · '}
                {onPick ? (
                  <button type="button" onClick={() => onPick(a.guide_id)} className="text-brand-800 underline hover:text-brand-950">
                    {label}
                  </button>
                ) : (
                  label
                )}
              </span>
            )
          })}
    </p>
  )
}

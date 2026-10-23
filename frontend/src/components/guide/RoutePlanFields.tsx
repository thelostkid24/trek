import type { RouteAvailability, RoutePlan } from '../../api/guide.ts'
import { MONTHS } from '../../lib/guideRecord.ts'
import { TextField } from '../auth/TextField.tsx'
import { SelectField } from '../profile/fields.tsx'

const AVAILABILITY: { value: RouteAvailability; label: string }[] = [
  { value: 'ASK', label: 'Ask me' },
  { value: 'ANY_TIME', label: 'Any time' },
  { value: 'FROM_DATE', label: 'From a date' },
  { value: 'NOT_OPEN', label: 'Not open yet' },
]

const MONTH_OPTIONS = MONTHS.map((m, i) => ({ value: String(i + 1), label: m }))

/**
 * When a guide can lead a route and their note on it (docs/TRD.md §7.21.8). "Any time" can be narrowed to a season;
 * "From a date" needs the date. `errors` are keyed by `${prefix}.field`, as the server sends them.
 */
export function RoutePlanFields({
  plan,
  onChange,
  name,
  errors,
  prefix,
}: {
  plan: RoutePlan
  onChange: (plan: RoutePlan) => void
  name: string
  errors: Record<string, string>
  prefix: string
}) {
  const set = (patch: Partial<RoutePlan>) => onChange({ ...plan, ...patch })
  const month = (v: string) => (v ? Number(v) : null)
  return (
    <div className="grid gap-3 sm:grid-cols-3">
      <SelectField
        label="Available"
        name={`${name}-availability`}
        placeholder="Ask me"
        options={AVAILABILITY.filter((a) => a.value !== 'ASK')}
        value={plan.availability === 'ASK' ? '' : plan.availability}
        onChange={(e) => {
          const availability = (e.target.value || 'ASK') as RouteAvailability
          set({
            availability,
            season_from_month: availability === 'ANY_TIME' ? plan.season_from_month : null,
            season_to_month: availability === 'ANY_TIME' ? plan.season_to_month : null,
            available_from: availability === 'FROM_DATE' ? plan.available_from : null,
          })
        }}
        error={errors[`${prefix}.availability`]}
      />
      {plan.availability === 'ANY_TIME' && (
        <>
          <SelectField
            label="Season from (optional)"
            name={`${name}-season-from`}
            placeholder="All year"
            options={MONTH_OPTIONS}
            value={plan.season_from_month === null ? '' : String(plan.season_from_month)}
            onChange={(e) => set({ season_from_month: month(e.target.value) })}
            error={errors[`${prefix}.season_from_month`]}
          />
          <SelectField
            label="Season to"
            name={`${name}-season-to`}
            placeholder="All year"
            options={MONTH_OPTIONS}
            value={plan.season_to_month === null ? '' : String(plan.season_to_month)}
            onChange={(e) => set({ season_to_month: month(e.target.value) })}
            error={errors[`${prefix}.season_to_month`]}
          />
        </>
      )}
      {plan.availability === 'FROM_DATE' && (
        <TextField
          label="From"
          name={`${name}-from`}
          type="date"
          required
          value={plan.available_from ?? ''}
          onChange={(e) => set({ available_from: e.target.value || null })}
          error={errors[`${prefix}.available_from`]}
        />
      )}
      <div className="sm:col-span-3">
        <TextField
          label="Your note (optional)"
          name={`${name}-note`}
          maxLength={200}
          value={plan.note ?? ''}
          onChange={(e) => set({ note: e.target.value || null })}
          error={errors[`${prefix}.note`]}
          placeholder="E.g. My home winter route. I know which campsites stay out of the wind in January."
        />
      </div>
    </div>
  )
}

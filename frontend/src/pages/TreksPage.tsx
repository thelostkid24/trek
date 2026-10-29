import { useQuery } from '@tanstack/react-query'
import { useMemo, type ReactNode } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import {
  DIFFICULTY_LABEL,
  SEASON_LABEL,
  listCatalog,
  type CatalogTrek,
  type Difficulty,
  type Season,
} from '../api/catalog.ts'
import { messageFor } from '../auth/errorMessages.ts'
import { DifficultyPill } from '../components/catalog/DeparturePieces.tsx'
import { Ridgeline } from '../components/Ridgeline.tsx'
import { feet, monthKey, monthLabel } from '../lib/format.ts'
import { Seo } from '../components/Seo.tsx'

/** The grade filter's options, easiest first. The catalog's "moderate" reads as "Difficult" here. */
const GRADES: { grade: Difficulty; label: string }[] = [
  { grade: 'EASY', label: DIFFICULTY_LABEL.EASY },
  { grade: 'EASY_MODERATE', label: DIFFICULTY_LABEL.EASY_MODERATE },
  { grade: 'MODERATE', label: 'Difficult' },
  { grade: 'CHALLENGING', label: DIFFICULTY_LABEL.CHALLENGING },
]

/** A filter select, filled in once something is picked. */
/** /treks — every trek we run, filtered by season, month and grade. Contract: docs/TRD.md §7.9. */
export function TreksPage() {
  const [params, setParams] = useSearchParams()
  const catalog = useQuery({ queryKey: ['public-catalog'], queryFn: listCatalog })
  const treks = useMemo(() => catalog.data?.items ?? [], [catalog.data])

  const season = params.get('season') as Season | null
  const grade = params.get('grade') as Difficulty | null
  const month = params.get('month')

  /** Keeps the other filters when one changes, and drops a filter when it's picked again. */
  const setParam = (key: string, value: string | null) => {
    const next = new URLSearchParams(params)
    if (value === null || next.get(key) === value) next.delete(key)
    else next.set(key, value)
    setParams(next, { replace: true })
  }

  // Every month from now to the end of the winter season, so a month shows before its dates are published,
  // plus any later month that already has dates.
  const months = useMemo(() => {
    const keys = new Set([...seasonMonths(new Date()), ...treks.flatMap((t) => t.departures.map((d) => monthKey(d.start_date)))])
    return [...keys].sort()
  }, [treks])

  const matching = treks
    .filter((t) => !season || t.seasons.includes(season))
    .filter((t) => !grade || t.difficulty === grade)
    .map((t) => (month ? { ...t, departures: t.departures.filter((d) => monthKey(d.start_date) === month) } : t))
    // A month filter is about dates, so treks with none left drop out.
    .filter((t) => !month || t.departures.length > 0)

  return (
    <div className="mx-auto max-w-6xl px-4 py-8 sm:py-10">
      <Seo
        title="Himalayan treks in Uttarakhand"
        description="Every Himalayan trek we run in Uttarakhand, with upcoming dates, difficulty and the local guide leading each departure."
        path="/treks"
      />
      <header>
        <h1 className="font-display text-3xl font-light tracking-[-0.02em] sm:text-4xl">Our treks</h1>
      </header>

      <div className="mt-6 flex flex-wrap items-center gap-2">
        <Chip on={!season && !grade && !month} onClick={() => setParams(new URLSearchParams(), { replace: true })}>
          All
        </Chip>
        <FilterSelect label="Season" value={season} onChange={(v) => setParam('season', v)}>
          {(Object.keys(SEASON_LABEL) as Season[]).map((key) => (
            <option key={key} value={key}>
              {SEASON_LABEL[key]}
            </option>
          ))}
        </FilterSelect>
        {months.length > 0 && (
          <FilterSelect label="Month" value={month} onChange={(v) => setParam('month', v)}>
            {months.map((key) => (
              <option key={key} value={key}>
                {monthLabel(key)}
              </option>
            ))}
          </FilterSelect>
        )}
        <FilterSelect label="Grade" value={grade} onChange={(v) => setParam('grade', v)}>
          {GRADES.map((g) => (
            <option key={g.grade} value={g.grade}>
              {g.label}
            </option>
          ))}
        </FilterSelect>
      </div>

      {catalog.isPending ? (
        <ul className="mt-8 grid gap-5 sm:grid-cols-2 lg:grid-cols-4" aria-busy="true" aria-label="Loading treks">
          {[0, 1, 2, 3].map((i) => (
            <li key={i} className="h-72 animate-pulse rounded-2xl bg-stone-100" />
          ))}
        </ul>
      ) : catalog.isError ? (
        <Note>
          <p className="text-stone-700">{messageFor(catalog.error)}</p>
          <button
            type="button"
            onClick={() => void catalog.refetch()}
            className="mt-3 rounded-full bg-brand-900 px-5 py-2 text-sm font-medium text-white hover:bg-brand-800"
          >
            Try again
          </button>
        </Note>
      ) : matching.length === 0 ? (
        <Note>
          <p className="font-display text-xl text-stone-900">
            {treks.length === 0 ? 'The first treks are on their way' : 'Nothing matches those filters'}
          </p>
          <p className="mt-1 text-sm text-stone-600">
            {treks.length === 0
              ? 'Our guides are planning the season. Check back soon.'
              : month
                ? `Dates for ${monthLabel(month)} open soon. Try another month, or see every trek.`
                : 'Try a different season, month or grade.'}
          </p>
        </Note>
      ) : (
        <ul className="mt-8 grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
          {matching.map((t) => (
            <TrekCard key={t.slug} trek={t} />
          ))}
        </ul>
      )}
    </div>
  )
}

/** The month the winter season ends in (March). */
const SEASON_END_MONTH = 3

/** "YYYY-MM" keys from this month through the next March. */
function seasonMonths(today: Date): string[] {
  const keys: string[] = []
  let year = today.getFullYear()
  let month = today.getMonth() + 1
  const endYear = month > SEASON_END_MONTH ? year + 1 : year
  while (year < endYear || month <= SEASON_END_MONTH) {
    keys.push(`${year}-${String(month).padStart(2, '0')}`)
    month += 1
    if (month > 12) {
      month = 1
      year += 1
    }
  }
  return keys
}

/**
 * A small card, four to a row on wide screens: the photo, where it starts, the name and grade, days and height, and
 * when to go. Dates, price and the full story live on the trek page.
 */
function TrekCard({ trek }: { trek: CatalogTrek }) {
  return (
    <li className="group relative flex flex-col overflow-hidden rounded-xl bg-white ring-1 ring-stone-200 transition hover:-translate-y-1 hover:shadow-lg hover:shadow-stone-900/5">
      {/* Shares its name with the trek page hero, so the cover grows into it (see index.css). */}
      <div
        className="relative aspect-[8/5] overflow-hidden bg-brand-950"
        style={{ viewTransitionName: `trek-cover-${trek.slug}`, viewTransitionClass: 'trek-cover' }}
      >
        {trek.cover_url ? (
          <img src={trek.cover_url} alt="" loading="lazy" className="size-full object-cover transition-transform duration-700 group-hover:scale-105" />
        ) : (
          <Ridgeline className="absolute inset-0 size-full transition-transform duration-700 group-hover:scale-105" />
        )}
      </div>

      <div className="flex flex-1 flex-col p-4">
        <span className="truncate text-xs text-stone-500">{trek.region}</span>
        {/* The grade drops under the name when both don't fit, rather than squeezing it. */}
        <div className="mt-1.5 flex flex-wrap items-center justify-between gap-x-2 gap-y-1">
          <h2 className="font-display text-lg font-semibold leading-snug text-brand-950">
            {/* The whole card opens the trek through this link's overlay. */}
            <Link to={`/treks/${trek.slug}`} viewTransition className="after:absolute after:inset-0">
              {trek.name}
            </Link>
          </h2>
          <DifficultyPill difficulty={trek.difficulty} />
        </div>
        <p className="mt-1 text-sm text-stone-700">
          {trek.duration_days} {trek.duration_days === 1 ? 'day' : 'days'}
          {trek.max_altitude_m ? ` · ${feet(trek.max_altitude_m)}` : ''}
        </p>
        {trek.season_label && <p className="mt-1.5 text-sm text-stone-500">{trek.season_label}</p>}
      </div>
    </li>
  )
}

/** A season, month or grade dropdown that looks like the chips: no native box, our own chevron. Empty value = no filter. */
function FilterSelect({ label, value, onChange, children }: { label: string; value: string | null; onChange: (value: string | null) => void; children: ReactNode }) {
  const on = value !== null
  return (
    <span className="relative inline-flex">
      <select
        aria-label={label}
        value={value ?? ''}
        onChange={(e) => onChange(e.target.value || null)}
        className={`cursor-pointer appearance-none rounded-full py-1.5 pr-8 pl-3 text-sm transition ${
          on ? 'bg-brand-900 text-white' : 'bg-white text-stone-700 ring-1 ring-stone-300 hover:ring-brand-400'
        }`}
      >
        <option value="">{label}</option>
        {children}
      </select>
      <svg viewBox="0 0 12 12" className={`pointer-events-none absolute top-1/2 right-3 size-3 -translate-y-1/2 ${on ? 'text-white' : 'text-stone-500'}`} fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden="true">
        <path d="M3 4.5 6 7.5 9 4.5" />
      </svg>
    </span>
  )
}

function Chip({ on, onClick, children }: { on: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      aria-pressed={on}
      onClick={onClick}
      className={`rounded-full px-3 py-1.5 text-sm transition ${
        on ? 'bg-brand-900 text-white' : 'bg-white text-stone-700 ring-1 ring-stone-300 hover:ring-brand-400'
      }`}
    >
      {children}
    </button>
  )
}

function Note({ children }: { children: ReactNode }) {
  return <div className="mt-8 rounded-2xl border border-stone-200 bg-white p-8 text-center">{children}</div>
}

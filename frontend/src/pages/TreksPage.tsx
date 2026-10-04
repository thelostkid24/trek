import { useQuery } from '@tanstack/react-query'
import { useMemo, type ReactNode } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import {
  DIFFICULTY_LABEL,
  listCatalog,
  type CatalogDeparture,
  type CatalogTrek,
  type Difficulty,
} from '../api/catalog.ts'
import { messageFor } from '../auth/errorMessages.ts'
import { DifficultyPill } from '../components/catalog/DeparturePieces.tsx'
import { Ridgeline } from '../components/Ridgeline.tsx'
import { feet, monthKey, monthLabel, rupees, shortRange } from '../lib/format.ts'
import { Seo } from '../components/Seo.tsx'

/** The grade filter's options, easiest first. The catalog's "moderate" reads as "Difficult" here. */
const GRADES: { grade: Difficulty; label: string }[] = [
  { grade: 'EASY', label: DIFFICULTY_LABEL.EASY },
  { grade: 'EASY_MODERATE', label: DIFFICULTY_LABEL.EASY_MODERATE },
  { grade: 'MODERATE', label: 'Difficult' },
  { grade: 'CHALLENGING', label: DIFFICULTY_LABEL.CHALLENGING },
]

/** A filter select, filled in once something is picked. */
/** /treks — every trek we run, filtered by grade and month. Contract: docs/TRD.md §7.9. */
export function TreksPage() {
  const [params, setParams] = useSearchParams()
  const catalog = useQuery({ queryKey: ['public-catalog'], queryFn: listCatalog })
  const treks = useMemo(() => catalog.data?.items ?? [], [catalog.data])

  const grade = params.get('grade') as Difficulty | null
  const month = params.get('month')

  /** Keeps the other filters when one changes, and drops a filter when it's picked again. */
  const setParam = (key: string, value: string | null) => {
    const next = new URLSearchParams(params)
    if (value === null || next.get(key) === value) next.delete(key)
    else next.set(key, value)
    setParams(next, { replace: true })
  }

  const months = useMemo(() => {
    const keys = new Set(treks.flatMap((t) => t.departures.map((d) => monthKey(d.start_date))))
    return [...keys].sort()
  }, [treks])

  const matching = treks
    .filter((t) => !grade || t.difficulty === grade)
    .map((t) => (month ? { ...t, departures: t.departures.filter((d) => monthKey(d.start_date) === month) } : t))
    // A month filter is about dates, so treks with none left drop out.
    .filter((t) => !month || t.departures.length > 0)

  return (
    <div className="mx-auto max-w-6xl px-4 py-8 sm:py-10">
      <Seo title="Our treks" description="Every trek we run, with upcoming dates, difficulty and the local guide leading each departure." path="/treks" />
      <header>
        <h1 className="font-display text-3xl font-light tracking-[-0.02em] sm:text-4xl">Our treks</h1>
      </header>

      <div className="mt-6 flex flex-wrap items-center gap-2">
        <Chip on={!grade && !month} onClick={() => setParams(new URLSearchParams(), { replace: true })}>
          All
        </Chip>
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
        <ul className="mt-8 grid gap-6 sm:grid-cols-2 lg:grid-cols-3" aria-busy="true" aria-label="Loading treks">
          {[0, 1, 2].map((i) => (
            <li key={i} className="h-80 animate-pulse rounded-2xl bg-stone-100" />
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
            {treks.length === 0 ? 'Our guides are planning the season. Check back soon.' : 'Try a different month or grade.'}
          </p>
        </Note>
      ) : (
        <ul className="mt-8 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {matching.map((t) => (
            <TrekCard key={t.slug} trek={t} />
          ))}
        </ul>
      )}
    </div>
  )
}

function TrekCard({ trek }: { trek: CatalogTrek }) {
  const from = trek.departures.length > 0 ? Math.min(...trek.departures.map((d) => d.price_paise)) : null
  const dates = trek.departures.slice(0, 3)

  return (
    <li className="group relative flex flex-col overflow-hidden rounded-2xl bg-white ring-1 ring-stone-200 transition hover:-translate-y-1 hover:shadow-lg hover:shadow-stone-900/5">
      {/* Shares its name with the trek page hero, so the cover grows into it (see index.css). */}
      <div
        className="relative aspect-[4/3] overflow-hidden bg-brand-950"
        style={{ viewTransitionName: `trek-cover-${trek.slug}`, viewTransitionClass: 'trek-cover' }}
      >
        {trek.cover_url ? (
          <img src={trek.cover_url} alt="" className="size-full object-cover transition-transform duration-700 group-hover:scale-105" />
        ) : (
          <Ridgeline className="absolute inset-0 size-full transition-transform duration-700 group-hover:scale-105" />
        )}
      </div>

      <div className="flex flex-1 flex-col p-4">
        <div className="flex items-center justify-between gap-2">
          <span className="truncate text-xs text-stone-500">{trek.region}</span>
          <DifficultyPill difficulty={trek.difficulty} />
        </div>
        <h2 className="mt-2 font-display text-xl font-semibold leading-snug text-brand-950">
          {/* The card is clickable through this link's overlay; the dates below sit above it. */}
          <Link to={`/treks/${trek.slug}`} viewTransition className="after:absolute after:inset-0">
            {trek.name}
          </Link>
        </h2>
        <p className="mt-1 line-clamp-2 text-sm text-stone-600">{trek.summary}</p>
        <p className="mt-2 text-xs text-stone-500">
          {trek.duration_days} {trek.duration_days === 1 ? 'day' : 'days'}
          {trek.max_altitude_m ? ` · ${feet(trek.max_altitude_m)}` : ''}
          {from !== null ? ` · from ${rupees(from)}` : ''}
        </p>

        <div className="relative z-10 mt-4">
          {dates.length === 0 ? (
            <p className="text-sm text-stone-500">
              Dates coming soon{trek.season_label ? ` · ${trek.season_label}` : ''}
            </p>
          ) : (
            <ul className="flex flex-wrap gap-2">
              {dates.map((d) => (
                <li key={d.id}>
                  <Link
                    to={`/departures/${d.id}`}
                    viewTransition
                    className={`block rounded-full px-3 py-1.5 text-xs font-medium ring-1 transition ${
                      d.bookable
                        ? 'text-brand-800 ring-brand-300 hover:bg-brand-50'
                        : 'text-stone-500 ring-stone-200 hover:bg-stone-50'
                    }`}
                  >
                    {shortRange(d.start_date, d.end_date)}
                    <span className="ml-1.5 font-normal">{seatNote(d)}</span>
                  </Link>
                </li>
              ))}
              {trek.departures.length > dates.length && (
                <li>
                  <Link
                    to={`/treks/${trek.slug}`}
                    viewTransition
                    className="block rounded-full px-3 py-1.5 text-xs font-medium text-stone-600 ring-1 ring-stone-200 hover:bg-stone-50"
                  >
                    +{trek.departures.length - dates.length} more
                  </Link>
                </li>
              )}
            </ul>
          )}
        </div>
      </div>
    </li>
  )
}

function seatNote(d: CatalogDeparture) {
  if (d.seats_left === 0) return '· full'
  if (!d.bookable) return '· closed'
  return ''
}

/** A month or grade dropdown that looks like the chips: no native box, our own chevron. Empty value = no filter. */
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

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
import { Avatar } from '../components/Avatar.tsx'
import { DifficultyPill, SeatMeter } from '../components/catalog/DeparturePieces.tsx'
import { Ridgeline } from '../components/Ridgeline.tsx'
import { monthKey, monthLabel, rupees, shortRange, weekdaysAndYear } from '../lib/format.ts'

/** Duration buckets a trekker actually plans around. */
const LENGTHS = {
  day: { label: 'One day', fits: (days: number) => days === 1 },
  weekend: { label: 'Weekend', fits: (days: number) => days === 2 },
  long: { label: '3 days +', fits: (days: number) => days >= 3 },
}
type LengthKey = keyof typeof LENGTHS

const GRADES: Difficulty[] = ['EASY', 'MODERATE', 'CHALLENGING']

/** /treks — every trek we run, by trek or by date. Contract: docs/TRD.md §7.9. */
export function TreksPage() {
  const [params, setParams] = useSearchParams()
  const catalog = useQuery({ queryKey: ['public-catalog'], queryFn: listCatalog })
  const treks = useMemo(() => catalog.data?.items ?? [], [catalog.data])

  const byDate = params.get('view') === 'dates'
  const grade = params.get('grade') as Difficulty | null
  const length = params.get('length') as LengthKey | null
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
    .filter((t) => !length || LENGTHS[length].fits(t.duration_days))
    .map((t) => (month ? { ...t, departures: t.departures.filter((d) => monthKey(d.start_date) === month) } : t))
    // A month filter is about dates, so treks with none left drop out.
    .filter((t) => !month || t.departures.length > 0)

  return (
    <div className="mx-auto max-w-6xl px-4 py-8 sm:py-10">
      <header>
        <h1 className="font-display text-3xl font-semibold tracking-tight sm:text-4xl">Our treks</h1>
        <p className="mt-2 max-w-2xl text-stone-600">
          Every route we run, with the dates open on each. Batches of ten, one guide, no cancellations for low
          numbers.
        </p>
      </header>

      <div className="mt-6 flex flex-wrap items-center gap-2">
        {GRADES.map((g) => (
          <Chip key={g} on={grade === g} onClick={() => setParam('grade', g)}>
            {DIFFICULTY_LABEL[g]}
          </Chip>
        ))}
        <span className="mx-1 hidden h-5 w-px bg-stone-200 sm:block" />
        {(Object.keys(LENGTHS) as LengthKey[]).map((key) => (
          <Chip key={key} on={length === key} onClick={() => setParam('length', key)}>
            {LENGTHS[key].label}
          </Chip>
        ))}
        {months.length > 0 && (
          <>
            <span className="mx-1 hidden h-5 w-px bg-stone-200 sm:block" />
            <select
              aria-label="Month"
              value={month ?? ''}
              onChange={(e) => setParam('month', e.target.value || null)}
              className="rounded-full border border-stone-300 bg-white px-3 py-1.5 text-sm text-stone-800"
            >
              <option value="">Any month</option>
              {months.map((key) => (
                <option key={key} value={key}>
                  {monthLabel(key)}
                </option>
              ))}
            </select>
          </>
        )}

        <div className="ms-auto flex rounded-full bg-stone-100 p-1 text-sm">
          <Toggle on={!byDate} onClick={() => setParam('view', null)}>
            By trek
          </Toggle>
          <Toggle on={byDate} onClick={() => setParam('view', 'dates')}>
            By date
          </Toggle>
        </div>
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
      ) : byDate ? (
        <ByDate treks={matching} />
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
          {trek.max_altitude_m ? ` · ${trek.max_altitude_m.toLocaleString('en-IN')} m` : ''}
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

/** The same departures grouped by month, for trekkers whose dates are fixed. */
function ByDate({ treks }: { treks: CatalogTrek[] }) {
  const rows = treks
    .flatMap((t) => t.departures.map((d) => ({ trek: t, departure: d })))
    .sort((a, b) => a.departure.start_date.localeCompare(b.departure.start_date))
  const months = [...new Set(rows.map((r) => monthKey(r.departure.start_date)))]

  if (rows.length === 0) {
    return (
      <Note>
        <p className="font-display text-xl text-stone-900">No dates open yet</p>
        <p className="mt-1 text-sm text-stone-600">Switch to "By trek" to see the routes we run.</p>
      </Note>
    )
  }

  return (
    <div className="mt-8 space-y-8">
      {months.map((key) => (
        <section key={key}>
          <h2 className="font-display text-lg font-semibold text-stone-900">{monthLabel(key)}</h2>
          <ul className="mt-3 space-y-3">
            {rows
              .filter((r) => monthKey(r.departure.start_date) === key)
              .map(({ trek, departure: d }) => (
                <li
                  key={d.id}
                  className="rounded-2xl bg-white p-4 ring-1 ring-stone-200 transition hover:ring-brand-300"
                >
                  <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
                    <p className="font-display text-lg font-semibold">
                      <Link to={`/departures/${d.id}`} viewTransition className="hover:text-brand-800">
                        {shortRange(d.start_date, d.end_date)}
                      </Link>
                      <span className="ml-2 text-base font-normal text-stone-600">{trek.name}</span>
                    </p>
                    <span className="font-semibold">{rupees(d.price_paise)}</span>
                  </div>
                  <p className="text-xs text-stone-500">
                    {weekdaysAndYear(d.start_date, d.end_date)} · {trek.region} · {DIFFICULTY_LABEL[trek.difficulty]}
                  </p>
                  <div className="mt-3 sm:max-w-sm">
                    <SeatMeter size={d.max_group_size} left={d.seats_left} />
                  </div>
                  <div className="mt-3 flex flex-wrap items-center justify-between gap-3 border-t border-stone-100 pt-3">
                    <Link
                      to={`/guides/${d.guide.id}`}
                      viewTransition
                      className="flex items-center gap-2 text-sm text-stone-700 hover:text-brand-800"
                    >
                      <Avatar url={d.guide.avatar_url} name={d.guide.full_name} />
                      {d.guide.full_name ? `with ${d.guide.full_name}` : 'Local guide'}
                    </Link>
                    {d.bookable ? (
                      <Link
                        to={`/book/${d.id}`}
                        className="shrink-0 rounded-full bg-brand-900 px-4 py-2 text-sm font-medium text-white hover:bg-brand-800"
                      >
                        Book
                      </Link>
                    ) : (
                      <span className="shrink-0 text-xs font-medium text-stone-500">
                        {d.seats_left === 0 ? 'Full' : 'Closed'}
                      </span>
                    )}
                  </div>
                </li>
              ))}
          </ul>
        </section>
      ))}
    </div>
  )
}

function seatNote(d: CatalogDeparture) {
  if (d.seats_left === 0) return '· full'
  if (!d.bookable) return '· closed'
  return `· ${d.seats_left} left`
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

function Toggle({ on, onClick, children }: { on: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      aria-pressed={on}
      onClick={onClick}
      className={`rounded-full px-3 py-1 transition ${on ? 'bg-white text-stone-900 shadow-sm' : 'text-stone-600'}`}
    >
      {children}
    </button>
  )
}

function Note({ children }: { children: ReactNode }) {
  return <div className="mt-8 rounded-2xl border border-stone-200 bg-white p-8 text-center">{children}</div>
}

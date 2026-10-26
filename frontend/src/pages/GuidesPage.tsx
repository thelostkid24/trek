import { useQuery } from '@tanstack/react-query'
import { useId, useMemo, useState, type ReactNode } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { guidePath, listGuides, type GuideListItem } from '../api/catalog.ts'
import { messageFor } from '../auth/errorMessages.ts'
import { Avatar } from '../components/Avatar.tsx'
import { Seo } from '../components/Seo.tsx'
import { isCertified } from '../lib/trek.ts'

/** Cards per page. */
const PAGE_SIZE = 12
/** "Route verified" shows once a guide has led more than this many treks. */
const ROUTE_VERIFIED_AFTER = 7

/** /guides — every guide who leads with us, those with dates coming up first. Contract: docs/TRD.md §7.17. */
export function GuidesPage() {
  const [params, setParams] = useSearchParams()
  const guides = useQuery({ queryKey: ['public-guides'], queryFn: listGuides })
  const all = useMemo(() => guides.data?.items ?? [], [guides.data])

  const q = params.get('q') ?? ''
  const trek = params.get('trek')
  const state = params.get('state')
  const filtered = q.trim() !== '' || trek !== null || state !== null

  /** Sets one filter and goes back to page 1; an empty value drops it. */
  const setParam = (key: string, value: string | null) => {
    const next = new URLSearchParams(params)
    if (value) next.set(key, value)
    else next.delete(key)
    next.delete('page')
    setParams(next, { replace: true })
  }

  /** Every catalog trek any guide has led, with how many guides lead it. */
  const treks = useMemo(() => {
    const counts = new Map<string, { name: string; count: number }>()
    for (const g of all) {
      for (const t of g.treks) counts.set(t.slug, { name: t.name, count: (counts.get(t.slug)?.count ?? 0) + 1 })
    }
    return [...counts].map(([slug, v]) => ({ slug, ...v })).sort((a, b) => a.name.localeCompare(b.name))
  }, [all])

  /** Every state those treks are in, with how many guides lead a trek there. */
  const states = useMemo(() => {
    const counts = new Map<string, number>()
    for (const g of all) {
      for (const st of new Set(g.treks.flatMap((t) => (t.state ? [t.state] : [])))) counts.set(st, (counts.get(st) ?? 0) + 1)
    }
    return [...counts].map(([name, count]) => ({ name, count })).sort((a, b) => a.name.localeCompare(b.name))
  }, [all])

  const needle = q.trim().toLowerCase()
  const matching = all
    .filter((g) => !needle || (g.full_name ?? '').toLowerCase().includes(needle))
    .filter((g) => !trek || g.treks.some((t) => t.slug === trek))
    .filter((g) => !state || g.treks.some((t) => t.state === state))

  const pages = Math.max(1, Math.ceil(matching.length / PAGE_SIZE))
  const page = Math.min(Math.max(1, Number(params.get('page')) || 1), pages)
  const shown = matching.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE)

  const goTo = (n: number) => {
    const next = new URLSearchParams(params)
    if (n > 1) next.set('page', String(n))
    else next.delete('page')
    setParams(next)
    window.scrollTo({ top: 0 })
  }

  return (
    <div className="mx-auto max-w-6xl px-4 py-8 sm:py-10">
      <Seo
        title="Our trek guides"
        description="Meet the local mountain guides who lead our Himalayan treks in Uttarakhand: their certifications, the treks they know and what trekkers say."
        path="/guides"
      />
      <h1 className="font-display text-3xl font-light tracking-[-0.02em] sm:text-4xl">Our mountain heroes</h1>

      {guides.isPending ? (
        <ul className="mt-8 grid gap-6 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4" aria-busy="true" aria-label="Loading guides">
          {[0, 1, 2, 3].map((i) => (
            <li key={i} className="h-96 animate-pulse rounded-2xl bg-stone-100" />
          ))}
        </ul>
      ) : guides.isError ? (
        <Note>
          <p className="text-stone-700">{messageFor(guides.error)}</p>
          <button
            type="button"
            onClick={() => void guides.refetch()}
            className="mt-3 rounded-full bg-brand-900 px-5 py-2 text-sm font-medium text-white hover:bg-brand-800"
          >
            Try again
          </button>
        </Note>
      ) : all.length === 0 ? (
        <Note>
          <p className="font-display text-xl text-stone-900">Our guides are on their way</p>
          <p className="mt-1 text-sm text-stone-600">Check back soon to meet who you'll walk with.</p>
        </Note>
      ) : (
        <>
          <div role="search" className="mt-5 flex flex-col gap-2 rounded-xl bg-white p-3 ring-1 ring-stone-200">
            <div className="flex flex-wrap gap-2">
              <label className="relative min-w-0 flex-[1_1_20rem]">
                <span className="sr-only">Search by name</span>
                <svg viewBox="0 0 24 24" className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-stone-500" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
                  <circle cx="11" cy="11" r="7" />
                  <path d="M20 20l-3.5-3.5" />
                </svg>
                <input
                  type="search"
                  value={q}
                  onChange={(e) => setParam('q', e.target.value)}
                  placeholder="Search by name"
                  className="h-10 w-full rounded-lg bg-white pr-3 pl-9 text-base text-stone-900 sm:text-sm ring-1 ring-stone-300 placeholder:text-stone-500 focus:ring-2 focus:ring-brand-600 focus:outline-none"
                />
              </label>
              {treks.length > 0 && (
                <FilterSelect label="Filter by trek" all="All treks" value={trek} onChange={(v) => setParam('trek', v)}>
                  {treks.map((t) => (
                    <option key={t.slug} value={t.slug}>
                      {t.name} ({t.count})
                    </option>
                  ))}
                </FilterSelect>
              )}
              {states.length > 0 && (
                <FilterSelect label="Filter by state" all="All states" value={state} onChange={(v) => setParam('state', v)}>
                  {states.map((st) => (
                    <option key={st.name} value={st.name}>
                      {st.name} ({st.count})
                    </option>
                  ))}
                </FilterSelect>
              )}
            </div>
            <div className="flex flex-wrap items-center justify-between gap-3">
              <span aria-live="polite" className="text-xs text-stone-600">
                {filtered
                  ? `${matching.length} ${matching.length === 1 ? 'match' : 'matches'}`
                  : `Showing ${(page - 1) * PAGE_SIZE + 1}–${(page - 1) * PAGE_SIZE + shown.length} of ${all.length}`}
              </span>
              {filtered && <ClearAll onClick={() => setParams(new URLSearchParams(), { replace: true })} />}
            </div>
          </div>

          {shown.length === 0 ? (
            <div className="mt-8 flex flex-col items-center gap-2 rounded-xl border border-dashed border-stone-300 px-6 py-12 text-center">
              <p className="font-display text-xl font-semibold text-stone-900">No one matches those filters</p>
              <p className="text-[15px] text-stone-600">Check the spelling of the name, or pick another trek or state.</p>
              <ClearAll onClick={() => setParams(new URLSearchParams(), { replace: true })} />
            </div>
          ) : (
            <ul className="mt-8 grid gap-6 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 xl:gap-8">
              {shown.map((g) => (
                <GuideCard key={g.id} guide={g} />
              ))}
            </ul>
          )}

          {pages > 1 && <Pages page={page} pages={pages} onGo={goTo} />}
        </>
      )}
    </div>
  )
}

function GuideCard({ guide: g }: { guide: GuideListItem }) {
  const name = g.full_name ?? 'Local guide'
  const first = g.full_name?.trim().split(/\s+/)[0] ?? 'profile'
  const ledEnough = g.treks_led > ROUTE_VERIFIED_AFTER
  const routeVerified = ledEnough || g.route_verified

  return (
    <li className="group flex flex-col items-center gap-3.5 rounded-xl bg-white px-4 pt-[26px] pb-[18px] text-center ring-1 ring-stone-200 transition hover:ring-brand-800">
      <Avatar url={g.avatar_url} name={name} size="lg" />
      <div className="flex flex-col gap-0.5">
        <h2 className="font-display text-xl leading-[26px] font-semibold tracking-[-0.01em] text-stone-900">
          <Link to={guidePath(g)} viewTransition className="underline-offset-[3px] group-hover:underline">
            {name}
          </Link>
        </h2>
        {g.home_city && <p className="text-[13px] leading-[18px] text-stone-600">{g.home_city}</p>}
      </div>

      <div className="flex min-h-5 w-full justify-center gap-1">
        {isCertified(g) && (
          <InfoTag icon={<ShieldIcon />} label="Certified">
            <b className="font-semibold">Certified:</b> {first === 'profile' ? 'This guide' : first} has completed
            mountaineering training at a recognised institute, like NIM. We checked the certificate before listing them.
          </InfoTag>
        )}
        {routeVerified && (
          <InfoTag icon={<PeaksIcon />} label="Route verified">
            <b className="font-semibold">Route verified:</b> {first === 'profile' ? 'This guide' : first}{' '}
            {ledEnough
              ? `has led more than ${ROUTE_VERIFIED_AFTER} treks with us.`
              : 'knows the routes they lead. Our team has checked them.'}
          </InfoTag>
        )}
      </div>

      <dl className="grid w-full grid-cols-3 divide-x divide-stone-200 border-y border-stone-200">
        <Stat label="Years" value={g.years_leading} />
        <Stat label="Batches led" value={g.treks_led || null} />
        {/* No reviews yet reads "New", as on the guide page. */}
        <Stat label="Reviews" value={g.review_count || 'New'} />
      </dl>

      <Link
        to={guidePath(g)}
        viewTransition
        aria-label={`View ${name}'s profile`}
        className="mt-auto flex min-h-[46px] w-full items-center justify-center gap-2 rounded-lg bg-brand-900 text-[15px] font-semibold text-white transition group-hover:bg-brand-950 hover:bg-brand-950 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-600"
      >
        View {first}
        <svg viewBox="0 0 24 24" className="size-4" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <path d="M5 12h14M13 6l6 6-6 6" />
        </svg>
      </Link>
    </li>
  )
}

function Stat({ label, value }: { label: string; value: number | string | null }) {
  return (
    <div className="flex flex-col-reverse gap-px py-2">
      <dt className="text-[10px] leading-[14px] font-semibold tracking-[0.06em] whitespace-nowrap text-stone-600 uppercase">{label}</dt>
      <dd className={`font-mono text-[17px] leading-[22px] font-medium tabular-nums ${value === null ? 'text-stone-500' : 'text-stone-900'}`}>
        {value ?? '—'}
      </dd>
    </div>
  )
}

/** A small tag whose "i" opens an explanation above it: on hover or focus, or on tap on a phone. */
function InfoTag({ icon, label, children }: { icon: ReactNode; label: string; children: ReactNode }) {
  const [open, setOpen] = useState(false)
  const tipId = useId()
  return (
    <span className="group/tip relative inline-flex shrink-0 items-center gap-1 rounded whitespace-nowrap bg-brand-50 px-1.5 text-[11px] leading-5 font-semibold text-brand-800">
      {icon}
      {label}
      <button
        type="button"
        aria-label={`What does ${label} mean?`}
        aria-describedby={tipId}
        aria-expanded={open}
        onClick={() => setOpen((o) => !o)}
        onBlur={() => setOpen(false)}
        className="ml-0.5 inline-flex size-[15px] cursor-help items-center justify-center rounded-full border-[1.2px] border-current font-serif text-[10px] leading-none font-bold italic hover:border-brand-800 hover:bg-brand-800 hover:text-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-600"
      >
        i
      </button>
      <span
        id={tipId}
        role="tooltip"
        className={`pointer-events-none absolute bottom-[calc(100%+10px)] left-1/2 z-20 w-59 -translate-x-1/2 rounded-lg bg-stone-950 px-3 py-2.5 text-left text-xs leading-[18px] font-normal text-stone-100 transition-opacity after:absolute after:top-full after:left-1/2 after:-ml-1.5 after:border-6 after:border-transparent after:border-t-stone-950 group-focus-within/tip:opacity-100 group-hover/tip:opacity-100 ${
          open ? 'opacity-100' : 'opacity-0'
        }`}
      >
        {children}
      </span>
    </span>
  )
}

function Pages({ page, pages, onGo }: { page: number; pages: number; onGo: (n: number) => void }) {
  // First, last, and the current page with one either side; gaps become "…".
  const nums = [...new Set([1, page - 1, page, page + 1, pages])].filter((n) => n >= 1 && n <= pages).sort((a, b) => a - b)
  const cell =
    'inline-flex h-11 min-w-11 items-center justify-center rounded-lg font-mono text-sm ring-1 transition disabled:opacity-40'
  return (
    <nav aria-label="Pages" className="mt-8 flex flex-wrap items-center justify-center gap-2">
      <button type="button" aria-label="Previous page" disabled={page === 1} onClick={() => onGo(page - 1)} className={`${cell} bg-white text-stone-900 ring-stone-300 enabled:hover:ring-brand-800`}>
        ←
      </button>
      {nums.map((n, i) => (
        <span key={n} className="contents">
          {i > 0 && n - nums[i - 1] > 1 && <span className="px-1 text-stone-500">…</span>}
          <button
            type="button"
            aria-current={n === page ? 'page' : undefined}
            onClick={() => onGo(n)}
            className={`${cell} ${n === page ? 'bg-brand-900 text-white ring-brand-900' : 'bg-white text-stone-900 ring-stone-300 hover:ring-brand-800'}`}
          >
            {n}
          </button>
        </span>
      ))}
      <button type="button" aria-label="Next page" disabled={page === pages} onClick={() => onGo(page + 1)} className={`${cell} bg-white text-stone-900 ring-stone-300 enabled:hover:ring-brand-800`}>
        →
      </button>
    </nav>
  )
}

/** A trek or state dropdown, tinted green while it's set. Empty value = no filter. */
function FilterSelect({ label, all, value, onChange, children }: { label: string; all: string; value: string | null; onChange: (value: string | null) => void; children: ReactNode }) {
  return (
    <span className="relative inline-flex flex-[1_1_8rem] sm:flex-none">
      <select
        aria-label={label}
        value={value ?? ''}
        onChange={(e) => onChange(e.target.value || null)}
        className={`h-10 w-full cursor-pointer appearance-none rounded-lg pr-8 pl-3 text-base ring-1 focus:ring-2 focus:ring-brand-600 focus:outline-none sm:min-w-40 sm:text-sm ${
          value ? 'bg-brand-50 font-semibold text-brand-800 ring-brand-700' : 'bg-white text-stone-900 ring-stone-300'
        }`}
      >
        <option value="">{all}</option>
        {children}
      </select>
      <svg viewBox="0 0 12 12" className="pointer-events-none absolute top-1/2 right-3 size-3 -translate-y-1/2 text-current" fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden="true">
        <path d="M3 4.5 6 7.5 9 4.5" />
      </svg>
    </span>
  )
}

function ClearAll({ onClick }: { onClick: () => void }) {
  return (
    <button type="button" onClick={onClick} className="inline-flex min-h-6 items-center text-sm font-semibold text-brand-800 hover:underline">
      Clear all
    </button>
  )
}

function ShieldIcon() {
  return (
    <svg viewBox="0 0 24 24" className="size-[11px]" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M12 3l7 3v5c0 4.5-3 8.2-7 10-4-1.8-7-5.5-7-10V6l7-3z" />
      <path d="M9 12l2 2 4-4" />
    </svg>
  )
}

function PeaksIcon() {
  return (
    <svg viewBox="0 0 24 24" className="size-[11px]" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M3 20l6-10 4 6 3-4 5 8z" />
    </svg>
  )
}

function Note({ children }: { children: ReactNode }) {
  return <div className="mt-8 rounded-2xl border border-stone-200 bg-white p-8 text-center">{children}</div>
}

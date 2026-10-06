import { useQuery } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { listGuides, type GuideListItem } from '../api/catalog.ts'
import { messageFor } from '../auth/errorMessages.ts'
import { Avatar } from '../components/Avatar.tsx'
import { RatingLine } from '../components/catalog/GuideProfileCard.tsx'
import { Seo } from '../components/Seo.tsx'
import { isCertified } from '../lib/trek.ts'

/** Trek names shown on a card before "+N more". */
const TREKS_SHOWN = 3

/** /guides — every guide who leads with us, those with dates coming up first. Contract: docs/TRD.md §7.17. */
export function GuidesPage() {
  const guides = useQuery({ queryKey: ['public-guides'], queryFn: listGuides })

  return (
    <div className="mx-auto max-w-6xl px-4 py-8 sm:py-10">
      <Seo
        title="Our trek guides"
        description="Meet the local mountain guides who lead our Himalayan treks in Uttarakhand: their certifications, the treks they know and what trekkers say."
        path="/guides"
      />
      <header>
        <h1 className="font-display text-3xl font-light tracking-[-0.02em] sm:text-4xl">Our guides</h1>
        <p className="mt-2 max-w-2xl text-stone-600">
          Every batch is led by a local dai who knows the mountain. Read their record before you pick your dates.
        </p>
      </header>

      {guides.isPending ? (
        <ul className="mt-8 grid gap-6 sm:grid-cols-2 lg:grid-cols-3" aria-busy="true" aria-label="Loading guides">
          {[0, 1, 2].map((i) => (
            <li key={i} className="h-56 animate-pulse rounded-2xl bg-stone-100" />
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
      ) : guides.data.items.length === 0 ? (
        <Note>
          <p className="font-display text-xl text-stone-900">Our guides are on their way</p>
          <p className="mt-1 text-sm text-stone-600">Check back soon to meet who you'll walk with.</p>
        </Note>
      ) : (
        <ul className="mt-8 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {guides.data.items.map((g) => (
            <GuideTile key={g.id} guide={g} />
          ))}
        </ul>
      )}
    </div>
  )
}

function GuideTile({ guide: g }: { guide: GuideListItem }) {
  const name = g.full_name ?? 'Local guide'
  const record = [
    g.years_leading !== null && `${g.years_leading} ${g.years_leading === 1 ? 'yr' : 'yrs'} guiding`,
    g.treks_led > 0 && `Led ${g.treks_led} ${g.treks_led === 1 ? 'trek' : 'treks'}`,
    g.languages && `Speaks ${g.languages}`,
  ].filter(Boolean)
  const treks = g.treks.slice(0, TREKS_SHOWN)

  return (
    <li className="relative flex flex-col rounded-2xl bg-white p-5 ring-1 ring-stone-200 transition hover:-translate-y-1 hover:shadow-lg hover:shadow-stone-900/5">
      <div className="flex items-center gap-4">
        <Avatar url={g.avatar_url} name={name} size="md" />
        <div className="min-w-0">
          <h2 className="truncate font-serif text-xl leading-tight text-stone-900">
            {/* The card is clickable through this link's overlay. */}
            <Link to={`/guides/${g.id}`} viewTransition className="after:absolute after:inset-0">
              {name}
            </Link>
          </h2>
          {g.home_city && <p className="truncate text-sm text-stone-500">{g.home_city}</p>}
        </div>
      </div>

      <div className="mt-3 flex flex-wrap items-center gap-2 text-sm">
        <RatingLine rating={g.rating} count={g.review_count} />
        {isCertified(g) && (
          <span className="inline-flex items-center gap-1.5 rounded-full bg-brand-50 px-2.5 py-0.5 text-xs font-medium text-brand-800">
            <svg viewBox="0 0 20 20" className="size-3.5" fill="currentColor" aria-hidden="true">
              <path d="M10 1.5 3.5 4v5c0 4 2.8 7.6 6.5 9 3.7-1.4 6.5-5 6.5-9V4L10 1.5Zm-1 12L5.5 10l1.4-1.4L9 10.7l4.1-4.1 1.4 1.4L9 13.5Z" />
            </svg>
            Certified
          </span>
        )}
      </div>

      {record.length > 0 && <p className="mt-2 text-xs text-stone-500">{record.join(' · ')}</p>}

      {treks.length > 0 && (
        <p className="mt-3 text-sm text-stone-700">
          <span className="text-stone-500">Knows </span>
          {treks.map((t) => t.name).join(', ')}
          {g.treks.length > treks.length && <span className="text-stone-500"> +{g.treks.length - treks.length} more</span>}
        </p>
      )}

      <p className="mt-auto pt-4 text-sm font-medium text-brand-800">
        {g.upcoming > 0 ? `${g.upcoming} upcoming ${g.upcoming === 1 ? 'date' : 'dates'} →` : 'Know your guide →'}
      </p>
    </li>
  )
}

function Note({ children }: { children: ReactNode }) {
  return <div className="mt-8 rounded-2xl border border-stone-200 bg-white p-8 text-center">{children}</div>
}

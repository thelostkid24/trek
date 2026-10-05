import { useQuery } from '@tanstack/react-query'
import { useMemo } from 'react'
import { Link, useParams } from 'react-router-dom'
import { trekQueryOptions, type TrekPage as Trek } from '../api/catalog.ts'
import { ApiError } from '../api/client.ts'
import { messageFor } from '../auth/errorMessages.ts'
import { DayByDay } from '../components/catalog/DayByDay.tsx'
import { SnowReportPanel } from '../components/catalog/SnowReportPanel.tsx'
import { TrailPhotos } from '../components/catalog/TrailPhotos.tsx'
import { TrekDepartures } from '../components/catalog/TrekDepartures.tsx'
import {
  FactGrid,
  Inclusions,
  Overview,
  Safety,
  TabBar,
  TrekFaqs,
  TrekSection,
} from '../components/catalog/TrekSections.tsx'
import { Ridgeline } from '../components/Ridgeline.tsx'
import { Seo } from '../components/Seo.tsx'
import { trekJsonLd } from '../lib/structuredData.ts'

/** /treks/:slug — public. One trek, every upcoming departure, each with its own guide. Contract: docs/TRD.md §7.7. */
export function TrekPage() {
  const { slug = '' } = useParams()
  const trek = useQuery({
    ...trekQueryOptions(slug),
    retry: (count, error) => !(error instanceof ApiError && error.status === 404) && count < 2,
  })

  if (trek.isPending) {
    return (
      <div className="mx-auto max-w-6xl space-y-4 px-4 py-10" aria-busy="true" aria-label="Loading trek">
        <div className="h-56 animate-pulse rounded-2xl bg-stone-200" />
        <div className="h-72 animate-pulse rounded-2xl bg-stone-100" />
      </div>
    )
  }
  if (trek.isError) {
    const missing = trek.error instanceof ApiError && trek.error.status === 404
    return (
      <section className="mx-auto max-w-md px-4 py-16 text-center">
        <Seo title="Trek not found" noindex />
        <h1 className="font-display text-2xl font-semibold">{missing ? 'This trek is not available' : 'Something went wrong'}</h1>
        <p className="mt-2 text-stone-600">{missing ? 'It may have been renamed or removed.' : messageFor(trek.error)}</p>
        <Link to="/treks" className="mt-4 inline-block text-brand-700 underline">
          See all treks
        </Link>
      </section>
    )
  }
  return <Page trek={trek.data} />
}

function Page({ trek }: { trek: Trek }) {
  const { track, content } = trek
  const cover = track.photos[0]
  const hasSafety = content.SAFETY.length + content.SAFETY_CALLOUT.length + content.SAFETY_NOTE.length > 0

  // Only sections with something in them get a tab.
  const tabs = useMemo(
    () =>
      [
        { id: 'overview', label: 'Overview', show: true },
        { id: 'photos', label: 'Photos', show: track.photos.length > 0 },
        { id: 'days', label: 'Day by day', show: track.itinerary.length > 0 },
        { id: 'included', label: "What's included", show: content.INCLUDED.length + content.NOT_INCLUDED.length > 0 },
        { id: 'safety', label: 'Safety', show: hasSafety },
        { id: 'faq', label: 'FAQ', show: content.FAQ.length > 0 },
      ].filter((t) => t.show),
    [track, content, hasSafety],
  )

  return (
    <div className="bg-paper-50">
      <Seo
        title={trekTitle(track)}
        description={track.summary}
        path={`/treks/${track.slug}`}
        image={cover?.url}
        jsonLd={trekJsonLd(trek)}
      />
      {/* The first trek photo leads, tall: the trek's name at its top, the trek facts across its foot. Until there is a */}
      {/* photo, the ridgeline stands in. Shares its view-transition name with the catalog card, which grows into it. */}
      <section
        className="relative isolate flex min-h-[32rem] flex-col justify-between overflow-hidden bg-brand-950 sm:min-h-[40rem]"
        style={{ viewTransitionName: `trek-cover-${track.slug}`, viewTransitionClass: 'trek-cover' }}
      >
        {cover ? (
          <>
            <img src={cover.url} alt={cover.caption ?? ''} className="absolute inset-0 -z-20 size-full object-cover" />
            <div className="absolute inset-0 -z-10 bg-gradient-to-b from-black/55 via-black/10 to-black/80" aria-hidden="true" />
          </>
        ) : (
          <Ridgeline className="absolute inset-0 -z-10 h-full w-full" />
        )}
        <header className="mx-auto w-full max-w-6xl px-4 pt-8 sm:pt-12">
          <Link to="/treks" viewTransition className="text-sm text-white/85 hover:text-white">
            ← All treks
          </Link>
          <h1 className="mt-2 font-serif text-5xl font-light leading-none tracking-tight text-white drop-shadow-sm sm:text-7xl">{track.name}</h1>
        </header>
        <div className="mx-auto w-full max-w-6xl px-4 pt-16 pb-8 sm:pb-10">
          <FactGrid track={track} onPhoto className="grid grid-cols-2 gap-x-6 gap-y-4 sm:grid-cols-3 lg:max-w-[calc(100%-25rem)]" />
        </div>
      </section>

      <div className="mx-auto grid max-w-6xl gap-x-12 gap-y-8 px-4 py-8 sm:py-12 lg:grid-cols-[minmax(0,1fr)_22rem]">
        <aside className="lg:sticky lg:top-20 lg:col-start-2 lg:row-start-1 lg:max-h-[calc(100dvh-6rem)] lg:self-start lg:overflow-y-auto lg:pr-1">
          <TrekDepartures trek={trek} />
        </aside>

        <div className="min-w-0 space-y-8 lg:col-start-1 lg:row-start-1">
          <SnowReportPanel report={trek.snow_report} place={track.meeting_point} trekName={track.name} />
          <TabBar tabs={tabs} />

          <TrekSection id="overview" label="Overview">
            <Overview text={track.description} />
          </TrekSection>
          <TrailPhotos id="photos" photos={track.photos} trekName={track.name} />
          <DayByDay id="days" days={track.itinerary} />
          {tabs.some((t) => t.id === 'included') && (
            <TrekSection id="included" label="What's included and not included">
              <Inclusions included={content.INCLUDED} excluded={content.NOT_INCLUDED} />
            </TrekSection>
          )}
          {hasSafety && (
            <TrekSection id="safety" label="Safety">
              <Safety callouts={content.SAFETY_CALLOUT} checklist={content.SAFETY} notes={content.SAFETY_NOTE} />
            </TrekSection>
          )}
          {content.FAQ.length > 0 && (
            <TrekSection id="faq" label="FAQ">
              <TrekFaqs items={content.FAQ} />
            </TrekSection>
          )}
        </div>
      </div>
    </div>
  )
}

/** "Kedarkantha Trek, Sankri, …": "trek" is the word people search with. Same as scripts/prerender.mjs. */
function trekTitle(track: { name: string; region: string }) {
  const name = /\btrek\b/i.test(track.name) ? track.name : `${track.name} Trek`
  return `${name}, ${track.region}`
}

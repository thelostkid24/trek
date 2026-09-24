import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { listGuideTracks } from '../../api/admin.ts'
import { useAuth } from '../../auth/useAuth.ts'
import { ErrorNote, Loading, Panel } from '../../components/admin/AdminUi.tsx'
import { SnowReports } from '../../components/admin/SnowReports.tsx'

/**
 * /guide — a guide files the weekly snow report for the treks they lead (docs/TRD.md §7.13). Rendered inside
 * <RequireAuth role="GUIDE">.
 */
export function GuideReportsPage() {
  const { withAuth } = useAuth()
  const tracks = useQuery({ queryKey: ['guide-tracks'], queryFn: () => withAuth(listGuideTracks) })
  const [picked, setPicked] = useState<string | null>(null)

  return (
    <div className="mx-auto max-w-3xl space-y-5 px-4 py-6 sm:py-10">
      <h1 className="font-display text-2xl font-semibold">Snow reports</h1>
      {tracks.isPending ? (
        <Loading />
      ) : tracks.isError ? (
        <ErrorNote error={tracks.error} />
      ) : tracks.data.items.length === 0 ? (
        <Panel title="No treks yet">
          <p className="text-sm text-stone-600">
            You can report on a trek once you have a published departure on it. Ask the team if one is missing.
          </p>
        </Panel>
      ) : (
        <GuideTrackReports tracks={tracks.data.items} picked={picked ?? tracks.data.items[0].id} onPick={setPicked} />
      )}
    </div>
  )
}

function GuideTrackReports({
  tracks,
  picked,
  onPick,
}: {
  tracks: { id: string; name: string }[]
  picked: string
  onPick: (id: string) => void
}) {
  const track = tracks.find((t) => t.id === picked) ?? tracks[0]
  return (
    <>
      {tracks.length > 1 && (
        <nav className="flex flex-wrap gap-2" aria-label="Your treks">
          {tracks.map((t) => (
            <button
              key={t.id}
              type="button"
              aria-pressed={t.id === track.id}
              onClick={() => onPick(t.id)}
              className={`rounded-full px-4 py-1.5 text-sm font-medium ${t.id === track.id ? 'bg-brand-900 text-white' : 'bg-white ring-1 ring-stone-300'}`}
            >
              {t.name}
            </button>
          ))}
        </nav>
      )}
      <h2 className="font-display text-xl font-semibold">{track.name}</h2>
      <SnowReports key={track.id} area="guide" trackId={track.id} place="" />
    </>
  )
}

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { guidePath } from '../../api/catalog.ts'
import { listSavedGuides, SAVED_GUIDES_KEY, unsaveGuide, type SavedGuide } from '../../api/savedGuides.ts'
import { messageFor } from '../../auth/errorMessages.ts'
import { useAuth } from '../../auth/useAuth.ts'
import { Avatar } from '../../components/Avatar.tsx'
import { PaperCard } from '../../components/trips/TripPieces.tsx'

/** /account/guides — the guides a trekker saved with "My Fav" (docs/TRD.md §7.21.7). Only they see this list. */
export function SavedGuidesPage() {
  const { withAuth } = useAuth()
  const list = useQuery({ queryKey: SAVED_GUIDES_KEY, queryFn: () => withAuth(listSavedGuides) })
  const items = list.data?.items ?? []

  return (
    <div className="px-5 py-8 sm:px-10 sm:py-10">
      <h1 className="sr-only">Saved guides</h1>
      {list.isPending ? (
        <div className="h-40 animate-pulse rounded-(--card-radius) bg-paper-200" aria-busy="true" aria-label="Loading saved guides" />
      ) : list.isError ? (
        <p className="text-stone-700">{messageFor(list.error)}</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-stone-600">
          Tap "My Fav" on a guide's page to keep them here.{' '}
          <Link to="/guides" className="text-pine-700 underline">
            Meet our guides
          </Link>
        </p>
      ) : (
        <>
          <p className="text-xs text-stone-500">Only you see this list. Guides aren't told when you save them.</p>
          <ul className="mt-3 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {items.map((g) => (
              <li key={g.guide_id}>
                <SavedGuideCard guide={g} />
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  )
}

function SavedGuideCard({ guide: g }: { guide: SavedGuide }) {
  const { withAuth } = useAuth()
  const queryClient = useQueryClient()
  const remove = useMutation({
    mutationFn: () => withAuth((token) => unsaveGuide(token, g.guide_id)),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: SAVED_GUIDES_KEY }),
  })
  const name = g.full_name ?? 'Local guide'
  return (
    <PaperCard className="flex items-center gap-4 p-4">
      <Avatar url={g.avatar_url} name={name} size="md" />
      <div className="min-w-0 flex-1">
        <Link to={guidePath({ id: g.guide_id, slug: g.slug })} className="block truncate font-medium text-ink-900 hover:underline">
          {name}
        </Link>
        <p className="truncate text-sm text-stone-600">
          {[g.home_city, g.rating !== null ? `★ ${g.rating.toFixed(1)} (${g.review_count})` : 'No reviews yet']
            .filter(Boolean)
            .join(' · ')}
        </p>
      </div>
      <button
        type="button"
        onClick={() => remove.mutate()}
        disabled={remove.isPending}
        aria-label={`Remove ${name} from saved guides`}
        className="shrink-0 text-sm text-stone-500 hover:text-laterite-600 disabled:opacity-50"
      >
        Remove
      </button>
    </PaperCard>
  )
}

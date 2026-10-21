import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useLocation, useNavigate } from 'react-router-dom'
import { listSavedGuides, SAVED_GUIDES_KEY, saveGuide, unsaveGuide } from '../../../api/savedGuides.ts'
import { useAuth } from '../../../auth/useAuth.ts'
import type { GuideSectionProps } from './types.ts'

/**
 * The "My Fav" heart (§7.21.7): a trekker saves the guide to a private list in their account. Visitors are sent to
 * sign in and come back here; the guide themself, other guides and admins don't see it.
 */
export function FavouriteButton({ guide, owner }: GuideSectionProps) {
  const auth = useAuth()
  const { withAuth } = auth
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()
  const trekker = auth.status === 'authenticated' && auth.user.role === 'TREKKER'
  const list = useQuery({ queryKey: SAVED_GUIDES_KEY, queryFn: () => withAuth(listSavedGuides), enabled: trekker })
  const saved = list.data?.items.some((g) => g.guide_id === guide.id) ?? false
  const toggle = useMutation({
    mutationFn: () => withAuth((token) => (saved ? unsaveGuide(token, guide.id) : saveGuide(token, guide.id))),
    onSettled: () => queryClient.invalidateQueries({ queryKey: SAVED_GUIDES_KEY }),
  })

  if (owner || auth.status === 'loading' || (auth.status === 'authenticated' && !trekker)) return null

  const onClick = () => {
    if (!trekker) {
      navigate('/login', { state: { from: location.pathname + location.search } })
      return
    }
    toggle.mutate()
  }
  // Shows the state the click is heading to while it saves.
  const on = toggle.isPending ? !saved : saved

  return (
    <button
      type="button"
      onClick={onClick}
      disabled={toggle.isPending || (trekker && list.isPending)}
      aria-pressed={trekker ? on : undefined}
      className="inline-flex items-center gap-2 rounded-full border border-paper-300 bg-white px-4 py-2 text-sm font-medium text-ink-900 transition hover:border-ink-900 disabled:opacity-60"
    >
      <svg
        viewBox="0 0 20 20"
        fill={on ? 'currentColor' : 'none'}
        stroke="currentColor"
        strokeWidth="1.6"
        className={`size-4 ${on ? 'text-laterite-600' : ''}`}
        aria-hidden="true"
      >
        <path
          d="M10 16.5s-6-3.6-6-8.1A3.4 3.4 0 0 1 10 6a3.4 3.4 0 0 1 6 2.4c0 4.5-6 8.1-6 8.1Z"
          strokeLinejoin="round"
        />
      </svg>
      <span aria-live="polite">{on ? 'Saved' : 'My Fav'}</span>
    </button>
  )
}

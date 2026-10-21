import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { getVisitorThread, replyAsVisitor } from '../api/questions.ts'
import { messageFor } from '../auth/errorMessages.ts'
import { ThreadView } from '../components/questions/QuestionInbox.tsx'

/**
 * /questions/:token (docs/TRD.md §7.21.6): a visitor's question thread, opened from the signed link in our emails.
 * No account needed; the link expires after 30 days and every email carries a fresh one.
 */
export function QuestionThreadPage() {
  const { token = '' } = useParams()
  const client = useQueryClient()
  const key = ['visitor-question', token]
  const thread = useQuery({ queryKey: key, queryFn: () => getVisitorThread(token), retry: false })
  const reply = useMutation({ mutationFn: (body: string) => replyAsVisitor(token, body) })

  return (
    <div className="mx-auto max-w-2xl px-4 py-8 sm:py-12">
      {thread.isPending ? (
        <div className="h-64 animate-pulse rounded-(--card-radius) bg-paper-200" aria-busy="true" aria-label="Loading" />
      ) : thread.isError ? (
        <div className="rounded-(--card-radius) border border-paper-300 bg-paper-50 p-8 text-center">
          <p className="text-stone-800">{messageFor(thread.error)}</p>
          <Link to="/guides" className="mt-4 inline-block text-sm font-medium text-brand-800 underline">
            Meet the guides
          </Link>
        </div>
      ) : (
        <>
          <h1 className="sr-only">Your question</h1>
          <ThreadView
            thread={thread.data}
            perspective="trekker"
            onReply={async (body) => {
              client.setQueryData(key, await reply.mutateAsync(body))
            }}
          />
          {thread.data.guide_slug && (
            <Link to={`/guides/${thread.data.guide_slug}`} className="mt-4 inline-block text-sm text-brand-700 hover:text-brand-900">
              See {thread.data.guide_name?.split(' ')[0] ?? 'the guide'}'s profile →
            </Link>
          )}
        </>
      )}
    </div>
  )
}

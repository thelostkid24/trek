import { useMutation } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import { deleteReply, replyToReview, tagLabel, type PublicReview } from '../../api/reviews.ts'
import { messageFor } from '../../auth/errorMessages.ts'
import { useAuth } from '../../auth/useAuth.ts'
import { parseDate } from '../../lib/format.ts'

const initials = (name: string) =>
  name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0]?.toUpperCase())
    .join('') || '?'

/** "Kedarkantha · Jan 2026 · 5/5" (a group-verified review has no date, so it shows when it was written). */
function meta(r: PublicReview) {
  const when = parseDate(r.trek_start_date ?? r.created_at.slice(0, 10)).toLocaleDateString('en-IN', {
    month: 'short',
    year: 'numeric',
  })
  return [r.trek_name, when, `${r.rating}/5`].filter(Boolean).join(' · ')
}

/**
 * One review on the guide's profile or in Guide → Reviews (docs/TRD.md §7.21.5). `guideFirst` names the replier.
 * With `onReplyChanged`, the guide can write, edit or remove their reply. `footer` adds guide/admin controls.
 */
export function ReviewCard({
  review: r,
  guideFirst,
  onReplyChanged,
  footer,
}: {
  review: PublicReview
  guideFirst: string
  onReplyChanged?: () => void
  footer?: ReactNode
}) {
  return (
    <li className="rounded-xl bg-white p-4 ring-1 ring-paper-300">
      <div className="flex items-start gap-3">
        <span
          aria-hidden
          className="grid size-10 shrink-0 place-items-center rounded-full bg-brand-100 text-sm font-semibold text-brand-800"
        >
          {initials(r.author_name)}
        </span>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <p className="font-medium text-stone-900">{r.author_name}</p>
            <span
              className={`rounded-full px-2 py-0.5 text-xs font-medium ${
                r.source === 'BOOKING' ? 'bg-brand-100 text-brand-800' : 'bg-paper-200 text-stone-700'
              }`}
            >
              {r.source === 'BOOKING' ? 'Verified trek' : 'Group verified'}
            </span>
          </div>
          <p className="mt-0.5 text-xs text-stone-500">{meta(r)}</p>
        </div>
      </div>
      {r.body && <p className="mt-3 whitespace-pre-line text-stone-700">{r.body}</p>}
      {r.tags.length > 0 && (
        <p className="mt-2 flex flex-wrap gap-1.5">
          {r.tags.map((t) => (
            <span key={t} className="rounded-full bg-paper-100 px-2 py-0.5 text-xs text-stone-600">
              {tagLabel(t)}
            </span>
          ))}
        </p>
      )}
      {onReplyChanged ? (
        <ReplyEditor review={r} guideFirst={guideFirst} onChanged={onReplyChanged} />
      ) : (
        r.reply && <ReplyText guideFirst={guideFirst} body={r.reply.body} />
      )}
      {footer}
    </li>
  )
}

function ReplyText({ guideFirst, body }: { guideFirst: string; body: string }) {
  return (
    <p className="mt-3 border-l-2 border-brand-300 pl-3 text-sm text-stone-700">
      <span className="font-medium text-stone-900">{guideFirst} replied: </span>
      {body}
    </p>
  )
}

function ReplyEditor({
  review: r,
  guideFirst,
  onChanged,
}: {
  review: PublicReview
  guideFirst: string
  onChanged: () => void
}) {
  const { withAuth } = useAuth()
  const [editing, setEditing] = useState(false)
  const [text, setText] = useState(r.reply?.body ?? '')
  const save = useMutation({
    mutationFn: () => withAuth((token) => replyToReview(token, r.id, text.trim())),
    onSuccess: () => {
      setEditing(false)
      onChanged()
    },
  })
  const remove = useMutation({
    mutationFn: () => withAuth((token) => deleteReply(token, r.id)),
    onSuccess: () => {
      setText('')
      onChanged()
    },
  })

  if (!editing) {
    return (
      <>
        {r.reply && <ReplyText guideFirst={guideFirst} body={r.reply.body} />}
        <div className="mt-2 flex gap-3 text-sm">
          <button type="button" onClick={() => setEditing(true)} className="font-medium text-pine-700 hover:underline">
            {r.reply ? 'Edit reply' : 'Reply publicly'}
          </button>
          {r.reply && (
            <button
              type="button"
              disabled={remove.isPending}
              onClick={() => remove.mutate()}
              className="text-stone-500 hover:underline"
            >
              Remove reply
            </button>
          )}
        </div>
        {remove.error && <p className="mt-1 text-sm text-laterite-600">{messageFor(remove.error)}</p>}
      </>
    )
  }
  return (
    <form
      className="mt-3 space-y-2"
      onSubmit={(e) => {
        e.preventDefault()
        if (text.trim()) save.mutate()
      }}
    >
      <label className="sr-only" htmlFor={`reply-${r.id}`}>
        Your public reply
      </label>
      <textarea
        id={`reply-${r.id}`}
        value={text}
        maxLength={1000}
        rows={3}
        onChange={(e) => setText(e.target.value)}
        placeholder="Thank them, or add context. Everyone can read this."
        className="w-full rounded-(--field-radius) border border-(--field-border) bg-(--field-bg) px-3 py-2 text-sm"
      />
      {save.error && <p className="text-sm text-laterite-600">{messageFor(save.error)}</p>}
      <div className="flex gap-2">
        <button
          type="submit"
          disabled={!text.trim() || save.isPending}
          className="rounded-full bg-pine-700 px-4 py-1.5 text-sm font-medium text-white disabled:opacity-50"
        >
          {save.isPending ? 'Posting…' : 'Post reply'}
        </button>
        <button type="button" onClick={() => setEditing(false)} className="px-3 text-sm text-stone-600">
          Cancel
        </button>
      </div>
    </form>
  )
}

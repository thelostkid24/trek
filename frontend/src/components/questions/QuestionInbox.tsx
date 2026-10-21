import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  MAX_MESSAGE,
  type AuthorRole,
  type QuestionThread,
  type QuestionThreadSummary,
} from '../../api/questions.ts'
import { messageFor } from '../../auth/errorMessages.ts'
import { dateTime } from '../../lib/format.ts'

/** Whose inbox this is: decides which side's messages sit on the right and what each row is called. */
export type Perspective = 'guide' | 'trekker' | 'admin'

const MINE: Record<Perspective, AuthorRole> = { guide: 'GUIDE', trekker: 'TREKKER', admin: 'ADMIN' }

/**
 * A two-pane inbox (§7.21.6) for the guide, the trekker and admins: threads on the left, the one in the address
 * (`{base}/:id`) on the right; on a phone, one at a time. Each page passes its own calls.
 */
export function QuestionInbox({
  perspective,
  base,
  queryKey,
  list,
  get,
  reply,
  empty,
  threadActions,
}: {
  perspective: Perspective
  base: string
  queryKey: unknown[]
  list: () => Promise<{ items: QuestionThreadSummary[] }>
  get: (id: string) => Promise<QuestionThread>
  reply?: (id: string, body: string) => Promise<QuestionThread>
  empty: ReactNode
  /** Extra controls under an open thread (admin close). */
  threadActions?: (thread: QuestionThread) => ReactNode
}) {
  const { id } = useParams()
  const threads = useQuery({ queryKey, queryFn: list })

  if (threads.isPending) {
    return <div className="h-72 animate-pulse rounded-(--card-radius) bg-paper-200" aria-busy="true" aria-label="Loading questions" />
  }
  if (threads.isError) return <p className="text-stone-700">{messageFor(threads.error)}</p>
  if (threads.data.items.length === 0 && !id) return <>{empty}</>

  return (
    <div className="grid gap-5 md:grid-cols-[20rem_1fr] md:items-start">
      <ul className={`space-y-2 ${id ? 'hidden md:block' : ''}`}>
        {threads.data.items.map((t) => (
          <li key={t.id}>
            <ThreadRow thread={t} perspective={perspective} to={`${base}/${t.id}`} active={t.id === id} />
          </li>
        ))}
      </ul>
      {id ? (
        <div className="min-w-0">
          <Link to={base} className="text-sm text-brand-700 hover:text-brand-900 md:hidden">
            ← All questions
          </Link>
          <OpenThread
            key={id}
            id={id}
            perspective={perspective}
            listKey={queryKey}
            get={get}
            reply={reply}
            threadActions={threadActions}
          />
        </div>
      ) : (
        <p className="hidden rounded-(--card-radius) border border-dashed border-paper-300 p-8 text-center text-sm text-stone-600 md:block">
          Pick a question to read it.
        </p>
      )}
    </div>
  )
}

function ThreadRow({
  thread: t,
  perspective,
  to,
  active,
}: {
  thread: QuestionThreadSummary
  perspective: Perspective
  to: string
  active: boolean
}) {
  const title =
    perspective === 'trekker' ? (t.guide_name ?? 'Guide') : perspective === 'guide' ? t.asker_name : `${t.asker_name} → ${t.guide_name ?? 'guide'}`
  const waiting = perspective === 'guide' && t.status === 'OPEN'
  return (
    <Link
      to={to}
      aria-current={active ? 'page' : undefined}
      className={`block rounded-xl border bg-white p-4 transition ${active ? 'border-ink-900' : 'border-paper-300 hover:border-stone-400'}`}
    >
      <div className="flex items-center justify-between gap-2">
        <p className={`truncate ${waiting ? 'font-semibold' : 'font-medium'} text-stone-900`}>{title}</p>
        <StatusChip status={t.status} perspective={perspective} />
      </div>
      {perspective === 'admin' && t.asker_email && <p className="truncate text-xs text-stone-500">{t.asker_email}</p>}
      <p className="mt-1 line-clamp-2 text-sm text-stone-600">{t.preview}</p>
      <p className="mt-1 text-xs text-stone-500">{dateTime(t.last_message_at)}</p>
    </Link>
  )
}

function StatusChip({ status, perspective }: { status: QuestionThreadSummary['status']; perspective: Perspective }) {
  const label =
    status === 'CLOSED'
      ? 'Closed'
      : status === 'ANSWERED'
        ? 'Answered'
        : perspective === 'guide'
          ? 'Needs reply'
          : 'Waiting'
  const tone =
    status === 'OPEN' && perspective === 'guide'
      ? 'bg-laterite-100 text-laterite-600'
      : status === 'ANSWERED'
        ? 'bg-brand-100 text-brand-800'
        : 'bg-stone-100 text-stone-600'
  return <span className={`shrink-0 rounded-full px-2.5 py-0.5 text-xs font-medium ${tone}`}>{label}</span>
}

function OpenThread({
  id,
  perspective,
  listKey,
  get,
  reply,
  threadActions,
}: {
  id: string
  perspective: Perspective
  listKey: unknown[]
  get: (id: string) => Promise<QuestionThread>
  reply?: (id: string, body: string) => Promise<QuestionThread>
  threadActions?: (thread: QuestionThread) => ReactNode
}) {
  const client = useQueryClient()
  const key = [...listKey, id]
  const thread = useQuery({ queryKey: key, queryFn: () => get(id) })
  if (thread.isPending) return <div className="mt-3 h-48 animate-pulse rounded-(--card-radius) bg-paper-200" aria-busy="true" />
  if (thread.isError) return <p className="mt-3 text-stone-700">{messageFor(thread.error)}</p>
  const t = thread.data
  return (
    <ThreadView
      thread={t}
      perspective={perspective}
      onReply={
        reply &&
        (async (body) => {
          const updated = await reply(id, body)
          client.setQueryData(key, updated)
          void client.invalidateQueries({ queryKey: listKey, exact: true })
        })
      }
      footer={threadActions?.(t)}
    />
  )
}

/** One thread: messages oldest first, then a reply box while it's open. Also used on the visitor's link page. */
export function ThreadView({
  thread: t,
  perspective,
  onReply,
  footer,
}: {
  thread: QuestionThread
  perspective: Perspective
  onReply?: (body: string) => Promise<void>
  footer?: ReactNode
}) {
  const heading = perspective === 'trekker' ? `Your question to ${t.guide_name ?? 'the guide'}` : `From ${t.asker_name}`
  return (
    <section className="mt-3 rounded-(--card-radius) border border-paper-300 bg-white p-5 sm:p-6 md:mt-0">
      <h2 className="font-display text-lg text-stone-900">{heading}</h2>
      {perspective === 'admin' && (
        <p className="mt-1 text-xs text-stone-500">
          To {t.guide_name ?? 'guide'} · from {t.asker_name}
          {t.asker_email ? ` (${t.asker_email}${t.visitor ? ', visitor' : ''})` : ''}
        </p>
      )}
      <ol className="mt-4 space-y-3">
        {t.messages.map((m) => {
          const mine = m.author_role === MINE[perspective]
          return (
            <li key={m.id} className={`flex ${mine ? 'justify-end' : 'justify-start'}`}>
              <div
                className={`max-w-[85%] rounded-2xl px-4 py-3 ${
                  m.author_role === 'ADMIN' ? 'bg-amber-50 ring-1 ring-amber-200' : mine ? 'bg-brand-900 text-white' : 'bg-paper-100'
                }`}
              >
                <p className={`text-xs ${mine && m.author_role !== 'ADMIN' ? 'text-white/70' : 'text-stone-500'}`}>
                  {m.author_name ?? ''} · {dateTime(m.created_at)}
                </p>
                <p className="mt-1 text-sm whitespace-pre-line">{m.body}</p>
              </div>
            </li>
          )
        })}
      </ol>
      {t.can_reply && onReply ? (
        <ReplyBox onReply={onReply} />
      ) : t.status === 'CLOSED' ? (
        <p className="mt-5 text-sm text-stone-600">This conversation was closed by The Empty Valley.</p>
      ) : null}
      {footer}
    </section>
  )
}

function ReplyBox({ onReply }: { onReply: (body: string) => Promise<void> }) {
  const [body, setBody] = useState('')
  const send = useMutation({ mutationFn: onReply, onSuccess: () => setBody('') })
  const text = body.trim()
  return (
    <form
      className="mt-5"
      onSubmit={(e) => {
        e.preventDefault()
        if (text) send.mutate(text)
      }}
    >
      <label htmlFor="question-reply" className="text-[0.65rem] font-medium tracking-[0.12em] text-stone-500 uppercase">
        Your reply
      </label>
      <textarea
        id="question-reply"
        value={body}
        onChange={(e) => setBody(e.target.value)}
        maxLength={MAX_MESSAGE}
        rows={4}
        className="mt-1.5 w-full rounded-(--field-radius) border border-(--field-border) bg-(--field-bg) px-3 py-2.5 text-stone-900 outline-none focus:ring-2 focus:ring-(--field-ring)"
      />
      <div className="mt-2 flex flex-wrap items-center justify-between gap-3">
        <p className="text-xs text-stone-500">Email addresses and phone numbers are removed; replies stay on the site.</p>
        <button
          type="submit"
          disabled={!text || send.isPending}
          className="rounded-full bg-brand-900 px-5 py-2 text-sm font-medium text-white hover:bg-brand-800 disabled:opacity-50"
        >
          {send.isPending ? 'Sending…' : 'Send reply'}
        </button>
      </div>
      {send.isError && (
        <p role="alert" className="mt-2 text-sm text-laterite-600">
          {messageFor(send.error)}
        </p>
      )}
    </form>
  )
}

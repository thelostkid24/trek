import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { getMyReviews, reportReview, type GuideReviewItem, type GuideReviews } from '../../api/reviews.ts'
import { messageFor } from '../../auth/errorMessages.ts'
import { useAuth } from '../../auth/useAuth.ts'
import { ErrorNote, Loading, Panel } from '../../components/admin/AdminUi.tsx'
import { QrCode } from '../../components/review/QrCode.tsx'
import { ReviewCard } from '../../components/review/ReviewCard.tsx'
import { parseDate } from '../../lib/format.ts'

const QUERY_KEY = ['my-reviews']

const monthYear = (iso: string) => parseDate(iso).toLocaleDateString('en-IN', { month: 'short', year: 'numeric' })

/**
 * /guide/reviews (docs/TRD.md §7.21.5): the verified rating, a shareable review link with WhatsApp text and a QR
 * code, and every review with Reply and Report. Rendered inside the guide layout.
 */
export function GuideReviewsPage() {
  const { withAuth } = useAuth()
  const reviews = useQuery({ queryKey: QUERY_KEY, queryFn: () => withAuth(getMyReviews) })

  return (
    <div className="mx-auto max-w-3xl space-y-6 px-5 py-6 sm:px-10">
      {reviews.isPending ? (
        <Loading />
      ) : reviews.isError ? (
        <ErrorNote error={reviews.error} />
      ) : (
        <>
          <Stats data={reviews.data} />
          <AskForReview data={reviews.data} />
          <ReviewsList data={reviews.data} />
        </>
      )}
    </div>
  )
}

function Stats({ data: d }: { data: GuideReviews }) {
  const stats = [
    { label: 'Rating', value: d.average === null ? '—' : `★ ${d.average.toFixed(1)}` },
    { label: 'Reviews', value: String(d.count) },
    { label: 'Replied this week', value: String(d.replied_this_week) },
  ]
  return (
    <section>
      <dl className="grid grid-cols-3 gap-3">
        {stats.map((s) => (
          <div key={s.label} className="rounded-2xl border border-stone-200 bg-white p-4">
            <dt className="text-xs text-stone-500">{s.label}</dt>
            <dd className="mt-1 text-2xl font-light text-stone-900">{s.value}</dd>
          </div>
        ))}
      </dl>
      <p className="mt-2 text-xs text-stone-500">
        Rating and count are from Empty Valley treks only.
        {d.own_trekker_count > 0 && ` Plus ${d.own_trekker_count} group-verified ${d.own_trekker_count === 1 ? 'review' : 'reviews'} on your page.`}
      </p>
    </section>
  )
}

function AskForReview({ data: d }: { data: GuideReviews }) {
  const [who, setWho] = useState<'EV' | 'OWN'>('EV')
  const [trek, setTrek] = useState(d.departures[0]?.trek_name ?? '')
  const [edited, setEdited] = useState<string | null>(null)
  const [showQr, setShowQr] = useState(false)
  const [copied, setCopied] = useState(false)
  const key = d.slug
  if (!key) return null
  const link = `${window.location.origin}/r/${key}${who === 'OWN' && trek.trim() ? `?trek=${encodeURIComponent(trek.trim())}` : ''}`
  const treks = [...new Set(d.departures.map((x) => x.trek_name))]
  const signIn = who === 'EV' ? ' (sign in with the account you booked with)' : ''
  const message =
    edited ??
    `Hi! It was great trekking ${trek.trim() || 'with you'}${trek.trim() ? ' with you' : ''}. Your words help other trekkers find me. Could you rate me here${signIn}? It takes 30 seconds: ${link}`

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(link)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      setCopied(false)
    }
  }

  return (
    <Panel title="Ask for a review">
      <div className="space-y-4">
        <fieldset>
          <legend className="text-sm font-medium text-stone-800">Who did you trek with?</legend>
          <div role="radiogroup" className="mt-2 flex flex-wrap gap-2">
            {([
              ['EV', 'Empty Valley trek'],
              ['OWN', 'My own trekkers'],
            ] as const).map(([v, label]) => (
              <button key={v} type="button" role="radio" aria-checked={who === v}
                onClick={() => { setWho(v); setEdited(null) }}
                className={`rounded-full px-3 py-1.5 text-sm ring-1 ${who === v ? 'bg-stone-900 text-white ring-stone-900' : 'bg-white text-stone-700 ring-stone-300'}`}>
                {label}
              </button>
            ))}
          </div>
          {who === 'OWN' && (
            <p className="mt-2 text-xs text-stone-500">
              Their reviews show as "Group verified" and don't count in your rating.
            </p>
          )}
        </fieldset>

        <div>
          <label htmlFor="ask-trek" className="block text-sm font-medium text-stone-800">Which trek?</label>
          {who === 'EV' && d.departures.length > 0 ? (
            <select id="ask-trek" value={trek} onChange={(e) => { setTrek(e.target.value); setEdited(null) }}
              className="mt-1.5 w-full rounded-(--field-radius) border border-(--field-border) bg-(--field-bg) px-3 py-2.5">
              {d.departures.map((x) => (
                <option key={x.departure_id} value={x.trek_name}>
                  {x.trek_name} · {monthYear(x.start_date)}
                </option>
              ))}
            </select>
          ) : (
            <input id="ask-trek" list="ask-trek-options" value={trek} maxLength={100}
              onChange={(e) => { setTrek(e.target.value); setEdited(null) }}
              placeholder="e.g. Hampta Pass"
              className="mt-1.5 w-full rounded-(--field-radius) border border-(--field-border) bg-(--field-bg) px-3 py-2.5" />
          )}
          <datalist id="ask-trek-options">
            {treks.map((t) => <option key={t} value={t}>{t}</option>)}
          </datalist>
        </div>

        <div>
          <label htmlFor="ask-message" className="block text-sm font-medium text-stone-800">Message</label>
          <textarea id="ask-message" rows={4} value={message} onChange={(e) => setEdited(e.target.value)}
            className="mt-1.5 w-full rounded-(--field-radius) border border-(--field-border) bg-(--field-bg) px-3 py-2.5 text-sm" />
        </div>

        <div className="flex flex-wrap gap-2">
          <a href={`https://wa.me/?text=${encodeURIComponent(message)}`} target="_blank" rel="noopener noreferrer"
            className="rounded-full bg-[#25D366] px-4 py-2 text-sm font-medium text-white hover:brightness-95">
            Send on WhatsApp
          </a>
          <button type="button" onClick={copy}
            className="rounded-full bg-white px-4 py-2 text-sm font-medium text-stone-800 ring-1 ring-stone-300">
            {copied ? 'Link copied' : 'Copy link'}
          </button>
          <button type="button" onClick={() => setShowQr((v) => !v)} aria-expanded={showQr}
            className="rounded-full bg-white px-4 py-2 text-sm font-medium text-stone-800 ring-1 ring-stone-300">
            {showQr ? 'Hide QR code' : 'Show QR code'}
          </button>
        </div>
        {showQr && (
          <div className="flex flex-col items-center gap-2 rounded-xl bg-paper-100 p-4">
            <QrCode text={link} label="QR code for your review link" />
            <p className="break-all text-center text-xs text-stone-500">{link}</p>
          </div>
        )}
      </div>
    </Panel>
  )
}

function ReviewsList({ data: d }: { data: GuideReviews }) {
  const queryClient = useQueryClient()
  const first = (d.full_name ?? 'You').split(' ')[0]
  const refresh = () => queryClient.invalidateQueries({ queryKey: QUERY_KEY })
  return (
    <Panel title={`Your reviews (${d.items.length})`}>
      {d.items.length === 0 ? (
        <p className="text-sm text-stone-600">No reviews yet. Share your link after a trek.</p>
      ) : (
        <ul className="grid gap-3">
          {d.items.map((item) => (
            <ReviewCard
              key={item.review.id}
              review={item.review}
              guideFirst={first}
              onReplyChanged={refresh}
              footer={<GuideControls item={item} onChanged={refresh} />}
            />
          ))}
        </ul>
      )}
    </Panel>
  )
}

function GuideControls({ item, onChanged }: { item: GuideReviewItem; onChanged: () => void }) {
  const { withAuth } = useAuth()
  const [open, setOpen] = useState(false)
  const [reason, setReason] = useState('')
  const report = useMutation({
    mutationFn: () => withAuth((token) => reportReview(token, item.review.id, reason.trim())),
    onSuccess: () => {
      setOpen(false)
      onChanged()
    },
  })
  return (
    <div className="mt-3 space-y-2 border-t border-paper-200 pt-3 text-sm">
      {item.hidden_reason && (
        <p className="rounded-lg bg-laterite-100 px-3 py-2 text-laterite-600">
          Hidden by our team: {item.hidden_reason}
        </p>
      )}
      {item.reply_hidden_reason && (
        <p className="rounded-lg bg-laterite-100 px-3 py-2 text-laterite-600">
          Your reply is hidden: {item.reply_hidden_reason}
        </p>
      )}
      {item.reported ? (
        <p className="text-stone-500">Reported — our team will take a look.</p>
      ) : open ? (
        <form className="space-y-2" onSubmit={(e) => { e.preventDefault(); if (reason.trim()) report.mutate() }}>
          <label htmlFor={`report-${item.review.id}`} className="block text-stone-700">
            What's wrong with this review?
          </label>
          <textarea id={`report-${item.review.id}`} rows={2} maxLength={500} value={reason}
            onChange={(e) => setReason(e.target.value)}
            className="w-full rounded-(--field-radius) border border-(--field-border) bg-(--field-bg) px-3 py-2" />
          {report.error && <p className="text-laterite-600">{messageFor(report.error)}</p>}
          <div className="flex gap-2">
            <button type="submit" disabled={!reason.trim() || report.isPending}
              className="rounded-full bg-stone-900 px-4 py-1.5 font-medium text-white disabled:opacity-50">
              Report
            </button>
            <button type="button" onClick={() => setOpen(false)} className="px-3 text-stone-600">Cancel</button>
          </div>
        </form>
      ) : (
        <button type="button" onClick={() => setOpen(true)} className="text-stone-500 hover:underline">
          Report
        </button>
      )}
    </div>
  )
}

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { getContent, replaceContent } from '../../api/admin.ts'
import type { ContentItem, ContentKind, TrekContent } from '../../api/catalog.ts'
import { fieldErrors } from '../../auth/errorMessages.ts'
import { useAuth } from '../../auth/useAuth.ts'
import { TextField } from '../auth/TextField.tsx'
import { TextAreaField } from '../profile/fields.tsx'
import { Button, ErrorNote, Loading, Panel } from './AdminUi.tsx'

const KINDS: { kind: ContentKind; label: string; hint: string; title?: string; badge?: boolean }[] = [
  { kind: 'INCLUDED', label: "What's included", hint: 'One line per item.' },
  { kind: 'NOT_INCLUDED', label: "What's not included", hint: 'One line per item.' },
  { kind: 'SAFETY_CALLOUT', label: 'Safety: dark box', hint: 'Only the first one shows.', title: 'Heading' },
  { kind: 'SAFETY', label: 'Safety checklist', hint: 'One line per item.' },
  { kind: 'SAFETY_NOTE', label: 'Safety notes', hint: 'Shown under the safety section, e.g. about insurance.' },
  { kind: 'FAQ', label: 'FAQ', hint: 'Question and answer.', title: 'Question' },
  { kind: 'WHY_US', label: 'Why choose us', hint: 'Cards: a short badge (1, NIM, 1%), a heading and a line or two.', title: 'Heading', badge: true },
]

/**
 * Edits the trek-page lists (docs/TRD.md §7.11). `trackId` null edits the shared lists every trek shows; a track's
 * own items follow the shared ones on its page. Each list saves on its own.
 */
export function ContentEditor({ trackId }: { trackId: string | null }) {
  const { withAuth } = useAuth()
  const queryKey = ['admin-content', trackId ?? 'shared']
  const content = useQuery({ queryKey, queryFn: () => withAuth((token) => getContent(token, trackId)) })

  if (content.isPending) return <Loading />
  if (content.isError) return <ErrorNote error={content.error} />
  return (
    <>
      {KINDS.map((k) => (
        <ListEditor key={k.kind} trackId={trackId} meta={k} saved={content.data[k.kind]} queryKey={queryKey} shared={trackId === null} />
      ))}
    </>
  )
}

function ListEditor({
  trackId,
  meta,
  saved,
  queryKey,
  shared,
}: {
  trackId: string | null
  meta: (typeof KINDS)[number]
  saved: ContentItem[]
  queryKey: string[]
  shared: boolean
}) {
  const { withAuth } = useAuth()
  const queryClient = useQueryClient()
  const [items, setItems] = useState<ContentItem[]>(saved)
  const save = useMutation({
    mutationFn: (list: ContentItem[]) => withAuth((token) => replaceContent(token, trackId, meta.kind, list)),
    onSuccess: (data: TrekContent) => {
      queryClient.setQueryData(queryKey, data)
      setItems(data[meta.kind])
    },
  })
  const errors = fieldErrors(save.error)
  const dirty = JSON.stringify(items) !== JSON.stringify(saved)
  const update = (i: number, patch: Partial<ContentItem>) => setItems((list) => list.map((it, j) => (j === i ? { ...it, ...patch } : it)))
  const move = (i: number, by: number) =>
    setItems((list) => {
      const next = [...list]
      ;[next[i], next[i + by]] = [next[i + by], next[i]]
      return next
    })

  return (
    <Panel
      title={`${meta.label} (${items.length})`}
      action={
        <Button disabled={!dirty || save.isPending} onClick={() => save.mutate(items)}>
          {save.isPending ? 'Saving…' : dirty ? 'Save' : 'Saved'}
        </Button>
      }
    >
      <p className="text-sm text-stone-600">
        {meta.hint} {shared ? 'Shown on every trek.' : 'Shown after the shared items on this trek only.'}
      </p>
      <ol className="mt-4 space-y-3">
        {items.map((item, i) => (
          <li key={i} className="rounded-xl border border-stone-200 p-3">
            <div className="grid gap-3 sm:grid-cols-[6rem_1fr]">
              {meta.badge && (
                <TextField label="Badge" name={`${meta.kind}-${i}-badge`} maxLength={12} value={item.badge ?? ''}
                  onChange={(e) => update(i, { badge: e.target.value || null })} error={errors[`items[${i}].badge`]} />
              )}
              {meta.title && (
                <div className={meta.badge ? '' : 'sm:col-span-2'}>
                  <TextField label={meta.title} name={`${meta.kind}-${i}-title`} maxLength={200} value={item.title ?? ''}
                    onChange={(e) => update(i, { title: e.target.value || null })} error={errors[`items[${i}].title`]} />
                </div>
              )}
              <div className="sm:col-span-2">
                <TextAreaField label={meta.title === 'Question' ? 'Answer' : meta.title ? 'Text' : `Item ${i + 1}`}
                  name={`${meta.kind}-${i}-body`} maxLength={2000} rows={meta.title ? 3 : 1} value={item.body}
                  onChange={(e) => update(i, { body: e.target.value })} error={errors[`items[${i}].body`]} />
              </div>
            </div>
            <div className="mt-2 flex gap-3 text-xs font-medium">
              <button type="button" disabled={i === 0} onClick={() => move(i, -1)} className="text-stone-600 hover:text-stone-900 disabled:opacity-40">
                Move up
              </button>
              <button type="button" disabled={i === items.length - 1} onClick={() => move(i, 1)} className="text-stone-600 hover:text-stone-900 disabled:opacity-40">
                Move down
              </button>
              <button type="button" onClick={() => setItems((list) => list.filter((_, j) => j !== i))} className="text-laterite-600 hover:text-laterite-500">
                Remove
              </button>
            </div>
          </li>
        ))}
      </ol>
      <div className="mt-3 flex flex-wrap items-center gap-3">
        <Button tone="secondary" disabled={items.length >= 40} onClick={() => setItems((list) => [...list, { badge: null, title: null, body: '' }])}>
          Add
        </Button>
        {dirty && (
          <button type="button" onClick={() => setItems(saved)} className="text-sm text-stone-600 hover:text-stone-900">
            Undo changes
          </button>
        )}
      </div>
      {save.error && Object.keys(errors).length === 0 && (
        <div className="mt-3">
          <ErrorNote error={save.error} />
        </div>
      )}
    </Panel>
  )
}

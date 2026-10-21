import { useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../../../auth/useAuth.ts'
import { messageFor } from '../../../auth/errorMessages.ts'
import { ApiError } from '../../../api/client.ts'
import {
  addGuideYouTubeVideo,
  deleteGuideMedia,
  getOwnGuideMedia,
  getPublicGuideMedia,
  MAX_CAPTION,
  MAX_MEDIA,
  MAX_PHOTO_BYTES,
  PHOTO_TYPES,
  reorderGuideMedia,
  updateGuideMediaCaption,
  uploadGuidePhoto,
  youTubeEmbedUrl,
  type GuideMediaItem,
} from '../../../api/guideMedia.ts'
import type { GuideSectionProps } from './types.ts'

const OWN_KEY = ['guide-media-own']

/**
 * Photos and YouTube videos (§7.21.4): a large stage with the picked item and a strip of thumbnails. A video shows
 * YouTube's still until it's played, then plays embedded from youtube-nocookie.com, so nothing loads from YouTube
 * before the visitor asks. Visitors see nothing until there is media; the owner adds, captions, reorders and
 * removes it here.
 */
export function MediaSection({ guide, owner }: GuideSectionProps) {
  const { withAuth } = useAuth()
  const publicMedia = useQuery({
    queryKey: ['guide-media', guide.id],
    queryFn: () => getPublicGuideMedia(guide.id),
    enabled: !owner,
  })
  const ownMedia = useQuery({ queryKey: OWN_KEY, queryFn: () => withAuth(getOwnGuideMedia), enabled: owner })
  const all = (owner ? ownMedia.data : publicMedia.data) ?? []
  const [editing, setEditing] = useState(false)

  if (!owner && all.length === 0) return null

  return (
    <section className="space-y-3" aria-label="Photos and videos">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="font-serif text-2xl text-ink-900">Photos and videos</h2>
        {owner && all.length > 0 && (
          <button
            type="button"
            onClick={() => setEditing((e) => !e)}
            className="rounded-full border border-paper-300 bg-white px-3 py-1.5 text-sm text-ink-900 hover:border-ink-900"
          >
            {editing ? 'Done' : 'Reorder or remove'}
          </button>
        )}
      </div>
      {editing ? <Editor items={all} /> : all.length > 0 && <Gallery items={all} />}
      {owner && <AddMedia count={all.length} />}
    </section>
  )
}

function Gallery({ items }: { items: GuideMediaItem[] }) {
  const [picked, setPicked] = useState(0)
  const [playing, setPlaying] = useState<string | null>(null)
  const index = Math.min(picked, items.length - 1)
  const item = items[index]
  const pick = (i: number) => {
    setPicked(i)
    setPlaying(null)
  }

  return (
    <div className="space-y-2">
      <div className="relative aspect-video overflow-hidden rounded-xl bg-ink-900">
        {item.kind === 'PHOTO' ? (
          <img src={item.url ?? undefined} alt={item.caption ?? 'Photo from the guide'} className="h-full w-full object-cover" />
        ) : playing === item.id && item.youtube_id && youTubeEmbedUrl(item.youtube_id) ? (
          <iframe
            key={item.id}
            src={youTubeEmbedUrl(item.youtube_id)!}
            title={item.caption ?? 'Video from the guide'}
            allow="autoplay; encrypted-media; picture-in-picture; fullscreen"
            allowFullScreen
            referrerPolicy="strict-origin-when-cross-origin"
            className="h-full w-full border-0"
          />
        ) : (
          <button
            type="button"
            onClick={() => setPlaying(item.id)}
            className="group absolute inset-0 h-full w-full"
            aria-label={`Play video${item.caption ? `: ${item.caption}` : ''}`}
          >
            {item.thumbnail_url && <img src={item.thumbnail_url} alt="" className="h-full w-full object-cover" />}
            <span className="absolute inset-0 m-auto flex h-16 w-16 items-center justify-center rounded-full bg-white/90 text-2xl text-ink-900 shadow-lg group-hover:bg-white">
              ▶
            </span>
          </button>
        )}
        {playing !== item.id && (
          <div className="pointer-events-none absolute inset-x-0 bottom-0 flex items-end justify-between gap-3 bg-gradient-to-t from-black/70 to-transparent px-4 pt-10 pb-3 text-white">
            <p className="min-w-0 text-sm">
              {item.kind === 'VIDEO' && (
                <span className="mr-2 text-xs font-semibold tracking-wider">VIDEO</span>
              )}
              {item.caption}
            </p>
            <span className="shrink-0 rounded-full bg-black/50 px-2 py-0.5 text-xs">
              {index + 1} / {items.length}
            </span>
          </div>
        )}
      </div>
      {items.length > 1 && (
        <ul className="flex gap-2 overflow-x-auto pb-1">
          {items.map((m, i) => (
            <li key={m.id} className="shrink-0">
              <button
                type="button"
                onClick={() => pick(i)}
                aria-current={i === index}
                className={`relative block h-16 w-24 overflow-hidden rounded-lg bg-ink-800 ring-2 sm:h-20 sm:w-32 ${i === index ? 'ring-laterite-500' : 'ring-transparent hover:ring-paper-300'}`}
              >
                {thumb(m) && <img src={thumb(m)!} alt="" className="h-full w-full object-cover" loading="lazy" />}
                <span className="absolute bottom-1 left-1 rounded bg-black/60 px-1.5 text-[11px] text-white">
                  {m.kind === 'VIDEO' ? '▶ Video' : 'Photo'}
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

function AddMedia({ count }: { count: number }) {
  const { withAuth } = useAuth()
  const queryClient = useQueryClient()
  const input = useRef<HTMLInputElement>(null)
  const [link, setLink] = useState('')
  const [showLink, setShowLink] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const full = count >= MAX_MEDIA
  const done = () => void queryClient.invalidateQueries({ queryKey: OWN_KEY })

  const photo = useMutation({
    mutationFn: (file: File) => {
      if (!PHOTO_TYPES.includes(file.type)) throw new Error('Choose a JPEG or PNG photo.')
      if (file.size > MAX_PHOTO_BYTES) throw new Error('That photo is over 5 MB. Please choose a smaller one.')
      return withAuth((token) => uploadGuidePhoto(token, file, null))
    },
    onMutate: () => setError(null),
    onError: (e) => setError(e instanceof ApiError ? messageFor(e) : e instanceof Error ? e.message : messageFor(e)),
    onSettled: done,
  })
  const video = useMutation({
    mutationFn: () => withAuth((token) => addGuideYouTubeVideo(token, link.trim(), null)),
    onMutate: () => setError(null),
    onSuccess: () => {
      setLink('')
      setShowLink(false)
    },
    onError: (e) => setError(messageFor(e)),
    onSettled: done,
  })
  const busy = photo.isPending || video.isPending

  return (
    <div className="space-y-2">
      <input
        ref={input}
        type="file"
        accept={PHOTO_TYPES.join(',')}
        className="hidden"
        onChange={(e) => {
          const file = e.target.files?.[0]
          e.target.value = ''
          if (file) photo.mutate(file)
        }}
      />
      {full ? (
        <p className="text-sm text-ink-700">{MAX_MEDIA} is the most a profile can have. Remove one to add another.</p>
      ) : (
        <div className="grid gap-2 sm:grid-cols-2">
          <button
            type="button"
            disabled={busy}
            onClick={() => input.current?.click()}
            className="rounded-xl border-2 border-dashed border-paper-300 px-4 py-4 text-sm font-medium text-ink-700 hover:border-ink-700 disabled:cursor-not-allowed disabled:opacity-60"
          >
            {photo.isPending ? 'Uploading photo…' : '+ Add photo'}
          </button>
          <button
            type="button"
            disabled={busy}
            aria-expanded={showLink}
            onClick={() => setShowLink((v) => !v)}
            className="rounded-xl border-2 border-dashed border-paper-300 px-4 py-4 text-sm font-medium text-ink-700 hover:border-ink-700 disabled:cursor-not-allowed disabled:opacity-60"
          >
            + Add YouTube video
          </button>
        </div>
      )}
      {showLink && !full && (
        <form
          onSubmit={(e) => {
            e.preventDefault()
            if (link.trim()) video.mutate()
          }}
          className="flex flex-col gap-2 sm:flex-row"
        >
          <input
            type="url"
            inputMode="url"
            value={link}
            onChange={(e) => setLink(e.target.value)}
            placeholder="https://youtu.be/…"
            aria-label="YouTube link"
            className="min-w-0 flex-1 rounded-lg border border-paper-300 px-3 py-2 text-sm"
          />
          <button
            type="submit"
            disabled={busy || !link.trim()}
            className="rounded-full bg-brand-900 px-5 py-2 text-sm font-semibold text-white hover:bg-brand-800 disabled:opacity-50"
          >
            {video.isPending ? 'Adding…' : 'Add video'}
          </button>
        </form>
      )}
      <p className="text-xs text-ink-400">
        Photos: JPEG or PNG up to 5 MB. Videos: paste a YouTube link (upload it to YouTube first; Shorts work too). It
        plays right here on your profile.
      </p>
      {error && <p className="text-sm text-sunset-500" role="alert">{error}</p>}
    </div>
  )
}

function Editor({ items }: { items: GuideMediaItem[] }) {
  const { withAuth } = useAuth()
  const queryClient = useQueryClient()
  const [error, setError] = useState<string | null>(null)
  const refresh = () => queryClient.invalidateQueries({ queryKey: OWN_KEY })

  const move = useMutation({
    mutationFn: ({ from, to }: { from: number; to: number }) => {
      const ids = items.map((m) => m.id)
      ;[ids[from], ids[to]] = [ids[to], ids[from]]
      return withAuth((token) => reorderGuideMedia(token, ids))
    },
    onSuccess: (data) => queryClient.setQueryData(OWN_KEY, data),
    onError: (e) => setError(messageFor(e)),
  })
  const remove = useMutation({
    mutationFn: (id: string) => withAuth((token) => deleteGuideMedia(token, id)),
    onSettled: refresh,
    onError: (e) => setError(messageFor(e)),
  })
  const caption = useMutation({
    mutationFn: ({ id, text }: { id: string; text: string }) =>
      withAuth((token) => updateGuideMediaCaption(token, id, text.trim() || null)),
    onSettled: refresh,
    onError: (e) => setError(messageFor(e)),
  })
  const busy = move.isPending || remove.isPending

  return (
    <div className="space-y-2">
      {error && <p className="text-sm text-sunset-500" role="alert">{error}</p>}
      <ul className="space-y-2">
        {items.map((m, i) => (
          <li key={m.id} className="flex items-center gap-3 rounded-xl bg-white p-2 ring-1 ring-paper-300">
            <div className="relative h-14 w-20 shrink-0 overflow-hidden rounded-lg bg-ink-800">
              {thumb(m) && <img src={thumb(m)!} alt="" className="h-full w-full object-cover" />}
              <span className="absolute bottom-0.5 left-0.5 rounded bg-black/60 px-1 text-[10px] text-white">
                {m.kind === 'VIDEO' ? '▶ Video' : 'Photo'}
              </span>
            </div>
            <div className="min-w-0 flex-1">
              <input
                  defaultValue={m.caption ?? ''}
                  maxLength={MAX_CAPTION}
                  placeholder="Add a caption"
                  aria-label="Caption"
                  onBlur={(e) => {
                    if (e.target.value.trim() !== (m.caption ?? '')) caption.mutate({ id: m.id, text: e.target.value })
                  }}
                  className="w-full rounded-lg border border-paper-300 px-2 py-1.5 text-sm"
                />
            </div>
            <div className="flex shrink-0 items-center gap-1">
              <IconButton label="Move left" disabled={busy || i === 0} onClick={() => move.mutate({ from: i, to: i - 1 })}>
                ←
              </IconButton>
              <IconButton
                label="Move right"
                disabled={busy || i === items.length - 1}
                onClick={() => move.mutate({ from: i, to: i + 1 })}
              >
                →
              </IconButton>
              <IconButton
                label="Remove"
                disabled={busy}
                onClick={() => {
                  if (window.confirm(`Remove this ${m.kind === 'VIDEO' ? 'video' : 'photo'} from your profile?`)) remove.mutate(m.id)
                }}
              >
                ✕
              </IconButton>
            </div>
          </li>
        ))}
      </ul>
    </div>
  )
}

function IconButton({ label, disabled, onClick, children }: { label: string; disabled: boolean; onClick: () => void; children: string }) {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      disabled={disabled}
      onClick={onClick}
      className="h-9 w-9 rounded-full border border-paper-300 text-sm text-ink-900 hover:border-ink-900 disabled:opacity-40"
    >
      {children}
    </button>
  )
}

function thumb(m: GuideMediaItem) {
  return m.kind === 'PHOTO' ? m.url : m.thumbnail_url
}

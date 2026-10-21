import { apiFetch } from './client.ts'

// Photos and YouTube videos on the guide profile (docs/TRD.md §7.21.4). The guide is the token's subject.

export type GuideMediaItem = {
  id: string
  kind: 'PHOTO' | 'VIDEO'
  caption: string | null
  position: number
  /** The photo; null for a video. */
  url: string | null
  /** A video's 11-character YouTube id; null for a photo. */
  youtube_id: string | null
  /** YouTube's still for a video; null for a photo. */
  thumbnail_url: string | null
  created_at: string
}

type MediaList = { items: GuideMediaItem[] }

export const MAX_MEDIA = 20
export const MAX_CAPTION = 120
export const MAX_PHOTO_BYTES = 5 * 1024 * 1024
export const PHOTO_TYPES = ['image/jpeg', 'image/png']

export const getPublicGuideMedia = (key: string) =>
  apiFetch<MediaList>(`/api/public/guides/${encodeURIComponent(key)}/media`).then((r) => r.items)

export const getOwnGuideMedia = (token: string) =>
  apiFetch<MediaList>('/api/guide/media', { token }).then((r) => r.items)

export function uploadGuidePhoto(token: string, file: Blob, caption: string | null) {
  const body = new FormData()
  body.append('file', file, 'photo.jpg')
  if (caption) body.append('caption', caption)
  return apiFetch<GuideMediaItem>('/api/guide/media/photos', { method: 'POST', token, body })
}

/** A YouTube link (watch, youtu.be, Shorts, embed or live); the server takes the video id from it. */
export const addGuideYouTubeVideo = (token: string, url: string, caption: string | null) =>
  apiFetch<GuideMediaItem>('/api/guide/media/videos', { method: 'POST', token, body: { url, caption } })

export const updateGuideMediaCaption = (token: string, id: string, caption: string | null) =>
  apiFetch<GuideMediaItem>(`/api/guide/media/${id}`, { method: 'PUT', token, body: { caption } })

export const reorderGuideMedia = (token: string, ids: string[]) =>
  apiFetch<MediaList>('/api/guide/media/order', { method: 'PUT', token, body: { ids } }).then((r) => r.items)

export const deleteGuideMedia = (token: string, id: string) =>
  apiFetch<void>(`/api/guide/media/${id}`, { method: 'DELETE', token })

/**
 * Where a video plays: YouTube's privacy-enhanced player, which sets no cookies until the visitor presses play.
 * The id is checked again here so nothing but an 11-character YouTube id ever reaches the iframe's src.
 */
export function youTubeEmbedUrl(youtubeId: string): string | null {
  return /^[A-Za-z0-9_-]{11}$/.test(youtubeId)
    ? `https://www.youtube-nocookie.com/embed/${youtubeId}?autoplay=1&rel=0&modestbranding=1&playsinline=1`
    : null
}

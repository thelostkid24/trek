import { ApiError, apiFetch } from './client.ts'

// Photos and videos on the guide profile (docs/TRD.md §7.21.4). The guide is the token's subject.

export type GuideMediaItem = {
  id: string
  kind: 'PHOTO' | 'VIDEO'
  caption: string | null
  position: number
  /** The photo, or for a video a link that redirects to a short-lived storage URL. */
  url: string
  /** A video's still; null until one is uploaded. */
  poster_url: string | null
  duration_s: number | null
  size_bytes: number
  /** A video is PENDING until its upload is confirmed; the public list has READY only. */
  status: 'PENDING' | 'READY'
  created_at: string
}

type MediaList = { items: GuideMediaItem[] }

export const MAX_MEDIA = 20
export const MAX_CAPTION = 120
export const MAX_PHOTO_BYTES = 5 * 1024 * 1024
export const MAX_VIDEO_BYTES = 100 * 1024 * 1024
export const MAX_VIDEO_SECONDS = 90
export const PHOTO_TYPES = ['image/jpeg', 'image/png']
export const VIDEO_TYPES = ['video/mp4', 'video/quicktime']

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

type VideoUpload = { id: string; upload_url: string; headers: Record<string, string> }

export const startGuideVideo = (
  token: string,
  body: { content_type: string; size_bytes: number; duration_s: number; caption: string | null },
) => apiFetch<VideoUpload>('/api/guide/media/videos', { method: 'POST', token, body })

export function uploadGuideVideoPoster(token: string, id: string, poster: Blob) {
  const body = new FormData()
  body.append('file', poster, 'poster.jpg')
  return apiFetch<GuideMediaItem>(`/api/guide/media/videos/${id}/poster`, { method: 'PUT', token, body })
}

export const completeGuideVideo = (token: string, id: string) =>
  apiFetch<GuideMediaItem>(`/api/guide/media/videos/${id}/complete`, { method: 'POST', token })

export const updateGuideMediaCaption = (token: string, id: string, caption: string | null) =>
  apiFetch<GuideMediaItem>(`/api/guide/media/${id}`, { method: 'PUT', token, body: { caption } })

export const reorderGuideMedia = (token: string, ids: string[]) =>
  apiFetch<MediaList>('/api/guide/media/order', { method: 'PUT', token, body: { ids } }).then((r) => r.items)

export const deleteGuideMedia = (token: string, id: string) =>
  apiFetch<void>(`/api/guide/media/${id}`, { method: 'DELETE', token })

/**
 * PUTs the file straight to storage (S3, or the dev backend) with the signed headers; no token, since the URL is
 * the permission. XHR rather than fetch for upload progress.
 */
export function putToStorage(upload: VideoUpload, file: Blob, onProgress: (fraction: number) => void) {
  return new Promise<void>((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('PUT', upload.upload_url)
    for (const [name, value] of Object.entries(upload.headers)) xhr.setRequestHeader(name, value)
    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable) onProgress(e.loaded / e.total)
    }
    xhr.onload = () =>
      xhr.status >= 200 && xhr.status < 300
        ? resolve()
        : reject(new ApiError(xhr.status, 'UPLOAD_FAILED', 'The upload did not go through'))
    xhr.onerror = () => reject(new ApiError(0, 'NETWORK_ERROR', 'Could not reach the server'))
    xhr.send(file)
  })
}

/** The video's length and a JPEG of a frame near the start, read in the browser (no transcoding anywhere). */
export function readVideo(file: File): Promise<{ duration: number; poster: Blob | null }> {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file)
    const video = document.createElement('video')
    video.preload = 'metadata'
    video.muted = true
    video.playsInline = true
    let duration = 0
    const done = (poster: Blob | null) => {
      URL.revokeObjectURL(url)
      resolve({ duration, poster })
    }
    video.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new ApiError(400, 'UNSUPPORTED_VIDEO', "This browser can't read that video"))
    }
    video.onloadedmetadata = () => {
      duration = video.duration
      if (!Number.isFinite(duration)) {
        video.onerror?.(new Event('error'))
        return
      }
      video.currentTime = Math.min(1, duration / 2)
    }
    video.onseeked = () => {
      const canvas = document.createElement('canvas')
      const scale = Math.min(1, 1280 / Math.max(video.videoWidth, video.videoHeight, 1))
      canvas.width = Math.max(1, Math.round(video.videoWidth * scale))
      canvas.height = Math.max(1, Math.round(video.videoHeight * scale))
      const ctx = canvas.getContext('2d')
      if (!ctx || !video.videoWidth) {
        done(null)
        return
      }
      ctx.drawImage(video, 0, 0, canvas.width, canvas.height)
      canvas.toBlob((blob) => done(blob), 'image/jpeg', 0.85)
    }
    video.src = url
  })
}

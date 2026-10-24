import { apiFetch } from './client.ts'

/** Reviews v2 (docs/TRD.md §7.21.5). */

export type ReviewSource = 'BOOKING' | 'OWN_TREKKER'

/** "What stood out?" — the fixed list, in display order. */
export const REVIEW_TAGS = [
  { code: 'FELT_SAFE', label: 'Felt safe' },
  { code: 'KNEW_THE_ROUTE', label: 'Knew the route' },
  { code: 'GOOD_PACE', label: 'Good pace' },
  { code: 'GREAT_FOOD', label: 'Great food' },
  { code: 'KNEW_THE_VILLAGES', label: 'Knew the villages' },
  { code: 'CALM_IN_BAD_WEATHER', label: 'Calm in bad weather' },
] as const
export type ReviewTag = (typeof REVIEW_TAGS)[number]['code']
export const tagLabel = (code: ReviewTag) => REVIEW_TAGS.find((t) => t.code === code)?.label ?? code

/** The four optional sub-ratings; `field` is the request/response key. */
export const SUB_RATINGS = [
  { key: 'SAFETY', field: 'safety_rating', label: 'Safety & decisions' },
  { key: 'ROUTE', field: 'route_rating', label: 'Route knowledge' },
  { key: 'COMMUNICATION', field: 'communication_rating', label: 'Communication' },
  { key: 'CARE', field: 'care_rating', label: 'Care at altitude' },
] as const
export type SubRatingField = (typeof SUB_RATINGS)[number]['field']
export type SubRatings = Partial<Record<SubRatingField, number | null>>

export type PublicReview = SubRatings & {
  id: string
  source: ReviewSource
  rating: number
  tags: ReviewTag[]
  /** A reviewer's own answer to "What stood out?", after picking "Other". */
  other_tag: string | null
  body: string | null
  author_name: string
  trek_name: string | null
  /** Null for reviews from a guide's own trekkers. */
  trek_start_date: string | null
  created_at: string
  reply: { body: string; created_at: string; updated_at: string } | null
}

export type ReviewPage = { items: PublicReview[]; total: number; page: number; size: number }

export type ReviewsSummary = {
  /** Verified (BOOKING) reviews only. */
  average: number | null
  count: number
  counts: { all: number; booking: number; own_trekker: number }
  /** OVERALL first, then sub-ratings with at least five answers. */
  categories: { key: 'OVERALL' | 'SAFETY' | 'ROUTE' | 'COMMUNICATION' | 'CARE'; average: number; answers: number }[]
  mentioned_often: { tag: ReviewTag; count: number }[]
}

export const getReviewsSummary = (key: string) =>
  apiFetch<ReviewsSummary>(`/api/public/guides/${encodeURIComponent(key)}/reviews-summary`)

export const getGuideReviews = (key: string, source: ReviewSource | null, page = 0, size = 10) =>
  apiFetch<ReviewPage>(
    `/api/public/guides/${encodeURIComponent(key)}/reviews?page=${page}&size=${size}${source ? `&source=${source}` : ''}`,
  )

/** No email or phone: the review waits for an admin to approve it (docs/TRD.md §7.21.5). */
export type OwnTrekkerReviewRequest = SubRatings & {
  rating: number
  tags: ReviewTag[]
  /** What they typed after picking "Other" under "What stood out?". */
  other_tag: string | null
  body: string | null
  name: string
  trek_name: string | null
}

export const postOwnTrekkerReview = (key: string, body: OwnTrekkerReviewRequest) =>
  apiFetch<{ review: PublicReview }>(`/api/public/guides/${encodeURIComponent(key)}/reviews`, {
    method: 'POST',
    body,
  })

/** 404 NO_REVIEWABLE_BOOKING when the trekker hasn't completed a trek with this guide. */
export const getReviewableBooking = (token: string, key: string) =>
  apiFetch<{ booking_id: string; reviewed: boolean }>(`/api/trekker/guides/${encodeURIComponent(key)}/review-booking`, {
    token,
  })

// Guide area.

export type GuideReviewItem = {
  review: PublicReview
  /** Set = an admin hid it. */
  hidden_reason: string | null
  reply_hidden_reason: string | null
  reported: boolean
}

export type GuideReviews = {
  slug: string | null
  full_name: string | null
  average: number | null
  count: number
  own_trekker_count: number
  replied_this_week: number
  departures: { departure_id: string; trek_name: string; start_date: string }[]
  items: GuideReviewItem[]
}

export const getMyReviews = (token: string) => apiFetch<GuideReviews>('/api/guide/reviews', { token })

export const replyToReview = (token: string, id: string, body: string) =>
  apiFetch<GuideReviewItem>(`/api/guide/reviews/${id}/reply`, { method: 'PUT', token, body: { body } })

export const deleteReply = (token: string, id: string) =>
  apiFetch<void>(`/api/guide/reviews/${id}/reply`, { method: 'DELETE', token })

export const reportReview = (token: string, id: string, reason: string) =>
  apiFetch<void>(`/api/guide/reviews/${id}/report`, { method: 'POST', token, body: { reason } })

// Admin.

export type AdminReviewView = 'WAITING' | 'RECENT' | 'REPORTED' | 'HIDDEN'

export type AdminReviewItem = {
  review: PublicReview
  guide_id: string
  guide_name: string | null
  guide_slug: string | null
  reviewer_contact: string | null
  /** Null while a review from a guide's own trekker waits for approval. */
  approved_at: string | null
  hidden_at: string | null
  hidden_reason: string | null
  reply_hidden_at: string | null
  reply_hidden_reason: string | null
  reports: { reason: string; reporter_name: string | null; created_at: string }[]
}

export const getAdminReviews = (token: string, view: AdminReviewView) =>
  apiFetch<{ items: AdminReviewItem[] }>(`/api/admin/reviews?view=${view}`, { token })

const adminPath = (id: string, reply: boolean) => `/api/admin/reviews/${id}${reply ? '/reply' : ''}/hidden`

export const approveReview = (token: string, id: string) =>
  apiFetch<AdminReviewItem>(`/api/admin/reviews/${id}/approved`, { method: 'PUT', token })

export const hideReview = (token: string, id: string, reply: boolean, reason: string) =>
  apiFetch<AdminReviewItem>(adminPath(id, reply), { method: 'PUT', token, body: { reason } })

export const unhideReview = (token: string, id: string, reply: boolean) =>
  apiFetch<AdminReviewItem>(adminPath(id, reply), { method: 'DELETE', token })

export const dismissReports = (token: string, id: string) =>
  apiFetch<AdminReviewItem>(`/api/admin/reviews/${id}/reports/dismiss`, { method: 'POST', token })

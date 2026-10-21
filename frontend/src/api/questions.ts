import { apiFetch } from './client.ts'

// Questions to a guide (docs/TRD.md §7.21.6). Replies happen on the site; contact details are never shared.

export type ThreadStatus = 'OPEN' | 'ANSWERED' | 'CLOSED'
export type AuthorRole = 'TREKKER' | 'GUIDE' | 'ADMIN'

export type QuestionMessage = {
  id: string
  author_role: AuthorRole
  /** The asker's name, the guide's name, or "The Empty Valley" for an admin. */
  author_name: string | null
  body: string
  created_at: string
}

export type QuestionThreadSummary = {
  id: string
  /** OPEN: waiting on the guide. ANSWERED: the guide wrote last. CLOSED: an admin closed it. */
  status: ThreadStatus
  guide_id: string
  guide_name: string | null
  guide_slug: string | null
  asker_name: string
  /** Admins only; null for everyone else. */
  asker_email: string | null
  visitor: boolean
  created_at: string
  last_message_at: string
  last_author_role: AuthorRole | null
  preview: string
}

export type QuestionThread = Omit<QuestionThreadSummary, 'last_message_at' | 'last_author_role' | 'preview'> & {
  first_reply_at: string | null
  closed_at: string | null
  can_reply: boolean
  messages: QuestionMessage[]
}

type Items<T> = { items: T[] }

/** Most characters in one message. */
export const MAX_MESSAGE = 2000

// Public: reply time, and visitors (email code first, then the signed link from our emails).

export const getReplyTime = (guideKey: string) =>
  apiFetch<{ median_minutes: number | null }>(`/api/public/guides/${encodeURIComponent(guideKey)}/reply-time`)

export const requestQuestionEmailCode = (email: string) =>
  apiFetch<{ expires_in: number; resend_after: number }>('/api/public/questions/email-code', {
    method: 'POST',
    body: { email },
  })

export const askAsVisitor = (
  guideKey: string,
  body: { name: string; email: string; email_code: string; body: string },
) => apiFetch<{ id: string }>(`/api/public/guides/${encodeURIComponent(guideKey)}/questions`, { method: 'POST', body })

export const getVisitorThread = (token: string) =>
  apiFetch<QuestionThread>(`/api/public/questions/${encodeURIComponent(token)}`)

export const replyAsVisitor = (token: string, body: string) =>
  apiFetch<QuestionThread>(`/api/public/questions/${encodeURIComponent(token)}/messages`, {
    method: 'POST',
    body: { body },
  })

// Signed-in trekker.

export const askAsTrekker = (token: string, guideKey: string, body: string) =>
  apiFetch<QuestionThread>(`/api/trekker/guides/${encodeURIComponent(guideKey)}/questions`, {
    method: 'POST',
    token,
    body: { body },
  })

export const listMyQuestions = (token: string) =>
  apiFetch<Items<QuestionThreadSummary>>('/api/trekker/questions', { token })

export const getMyQuestion = (token: string, id: string) =>
  apiFetch<QuestionThread>(`/api/trekker/questions/${id}`, { token })

export const replyToMyQuestion = (token: string, id: string, body: string) =>
  apiFetch<QuestionThread>(`/api/trekker/questions/${id}/messages`, { method: 'POST', token, body: { body } })

// The guide's inbox (unanswered first).

export const listGuideQuestions = (token: string) =>
  apiFetch<Items<QuestionThreadSummary>>('/api/guide/questions', { token })

export const getGuideQuestion = (token: string, id: string) =>
  apiFetch<QuestionThread>(`/api/guide/questions/${id}`, { token })

export const replyAsGuide = (token: string, id: string, body: string) =>
  apiFetch<QuestionThread>(`/api/guide/questions/${id}/messages`, { method: 'POST', token, body: { body } })

// Admins.

export const listAdminQuestions = (token: string, status?: ThreadStatus) =>
  apiFetch<Items<QuestionThreadSummary>>(`/api/admin/questions${status ? `?status=${status}` : ''}`, { token })

export const getAdminQuestion = (token: string, id: string) =>
  apiFetch<QuestionThread>(`/api/admin/questions/${id}`, { token })

export const closeQuestion = (token: string, id: string, note: string | null) =>
  apiFetch<QuestionThread>(`/api/admin/questions/${id}/close`, { method: 'POST', token, body: { note } })

/** "~45 min", "~2 h", "~3 days" for the reply-time stat. */
export function replyTimeLabel(minutes: number): string {
  if (minutes < 60) return `~${Math.max(1, Math.round(minutes))} min`
  const hours = Math.round(minutes / 60)
  if (hours < 48) return `~${hours} h`
  return `~${Math.round(hours / 24)} days`
}

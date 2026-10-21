import { apiFetch } from './client.ts'

// The days a guide is free to lead (docs/TRD.md §7.21.7). Only the guide and admins see them.

export type AvailabilityRange = { id: string; start_date: string; end_date: string; note: string | null }

export type AvailabilityInput = { start_date: string; end_date: string; note: string | null }

/** A guide's free range as admins see it beside the date pickers. */
export type GuideAvailability = AvailabilityRange & { guide_id: string; guide_name: string | null; slug: string | null }

export const listOwnAvailability = (token: string) =>
  apiFetch<{ items: AvailabilityRange[] }>('/api/guide/availability', { token })

export const createAvailability = (token: string, body: AvailabilityInput) =>
  apiFetch<AvailabilityRange>('/api/guide/availability', { method: 'POST', token, body })

export const updateAvailability = (token: string, id: string, body: AvailabilityInput) =>
  apiFetch<AvailabilityRange>(`/api/guide/availability/${id}`, { method: 'PUT', token, body })

export const deleteAvailability = (token: string, id: string) =>
  apiFetch<void>(`/api/guide/availability/${id}`, { method: 'DELETE', token })

/** Every guide's ranges sharing a day with [from, to] (at most 400 days apart). */
export const listGuideAvailability = (token: string, from: string, to: string) =>
  apiFetch<{ items: GuideAvailability[] }>(`/api/admin/guides/availability?from=${from}&to=${to}`, { token })

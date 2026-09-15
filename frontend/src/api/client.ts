const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8081'

/** Mirrors the backend error body: {code, message, details}. */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly details: Record<string, unknown>

  constructor(status: number, code: string, message: string, details: Record<string, unknown> = {}) {
    super(message)
    this.status = status
    this.code = code
    this.details = details
  }
}

type RequestOptions = Omit<RequestInit, 'body'> & { body?: unknown }

export async function apiFetch<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { body, headers, ...rest } = options
  let response: Response
  try {
    response = await fetch(`${BASE_URL}${path}`, {
      ...rest,
      headers: {
        Accept: 'application/json',
        ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
        ...headers,
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    })
  } catch {
    throw new ApiError(0, 'NETWORK_ERROR', 'Could not reach the server')
  }

  if (response.status === 204) return undefined as T

  const data = await response.json().catch(() => null)
  if (!response.ok) {
    throw new ApiError(
      response.status,
      data?.code ?? 'UNKNOWN_ERROR',
      data?.message ?? response.statusText,
      data?.details ?? {},
    )
  }
  return data as T
}

export type Health = { status: string; database: string; server_time: string }

export const getHealth = () => apiFetch<Health>('/api/public/health')

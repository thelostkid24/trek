import { useSyncExternalStore } from 'react'

// The visitor's answer to the cookie banner (docs/TRD.md §7.18). Only one thing waits on it: remembering the link a
// visitor arrived by (attribution.ts). The sign-in cookie, the theme and the release-reload marker are needed for
// the site to work and never ask. If storage is blocked, the answer lasts for this page only.

const KEY = 'tev.consent'
/** Bump when the banner starts asking about something new, so everyone is asked again. */
const VERSION = 1

export type Consent = 'granted' | 'denied'

type Stored = { analytics: boolean; at: string; version: number }

function load(): Consent | undefined {
  try {
    const raw = localStorage.getItem(KEY)
    if (!raw) return undefined
    const stored = JSON.parse(raw) as Stored
    if (stored.version !== VERSION) return undefined
    return stored.analytics ? 'granted' : 'denied'
  } catch {
    return undefined
  }
}

let current = load()
const listeners = new Set<() => void>()

/** undefined until the visitor answers. */
export function getConsent(): Consent | undefined {
  return current
}

export function setConsent(answer: Consent) {
  current = answer
  try {
    const stored: Stored = { analytics: answer === 'granted', at: new Date().toISOString(), version: VERSION }
    localStorage.setItem(KEY, JSON.stringify(stored))
  } catch {
    // Storage blocked: the answer holds until the page reloads.
  }
  for (const listener of listeners) listener()
}

export function onConsentChange(listener: () => void) {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

export function useConsent() {
  return useSyncExternalStore(onConsentChange, getConsent)
}

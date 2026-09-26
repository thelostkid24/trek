// Where visitors come from (docs/TRD.md §7.15). On every full page load we note the visit's campaign tags,
// ad click ids, outside referrer and landing page. The first visit is kept for good; the latest tagged or
// referred visit replaces the last one. Sign-up and booking requests carry both, and the server decides what
// to keep. Browser storage may be blocked or wiped; then we simply send less.

const FIRST_KEY = 'tev.first_touch'
const LAST_KEY = 'tev.last_touch'
const TAGS = ['utm_source', 'utm_medium', 'utm_campaign', 'utm_term', 'utm_content', 'gclid', 'fbclid'] as const

type Tag = (typeof TAGS)[number]

export type Touch = Partial<Record<Tag, string>> & {
  referrer?: string
  landing_path: string
  seen_at: string
}

export type DeviceType = 'MOBILE' | 'TABLET' | 'DESKTOP'

export type HeardFrom =
  | 'INSTAGRAM'
  | 'YOUTUBE'
  | 'GOOGLE_SEARCH'
  | 'FRIEND_FAMILY'
  | 'WHATSAPP_GROUP'
  | 'BLOG_FORUM'
  | 'OTHER'

export const HEARD_FROM_OPTIONS: { value: HeardFrom; label: string }[] = [
  { value: 'INSTAGRAM', label: 'Instagram' },
  { value: 'YOUTUBE', label: 'YouTube' },
  { value: 'GOOGLE_SEARCH', label: 'Google search' },
  { value: 'FRIEND_FAMILY', label: 'Friend or family' },
  { value: 'WHATSAPP_GROUP', label: 'WhatsApp group' },
  { value: 'BLOG_FORUM', label: 'Trekking blog or forum' },
  { value: 'OTHER', label: 'Other' },
]

/** Asked at sign-up and guest checkout; all optional. Consent boxes start unticked. */
export type SignupChoices = {
  heard_from?: HeardFrom
  heard_from_note?: string
  marketing_email?: boolean
  marketing_whatsapp?: boolean
}

export type Acquisition = SignupChoices & {
  first_touch?: Touch
  last_touch?: Touch
  device_type: DeviceType
}

function read(key: string): Touch | undefined {
  try {
    const raw = localStorage.getItem(key)
    return raw ? (JSON.parse(raw) as Touch) : undefined
  } catch {
    return undefined
  }
}

function write(key: string, touch: Touch) {
  try {
    localStorage.setItem(key, JSON.stringify(touch))
  } catch {
    // Storage blocked or full: this visit just isn't remembered.
  }
}

function externalReferrer(): string | undefined {
  try {
    if (!document.referrer) return undefined
    const ref = new URL(document.referrer)
    return ref.host === window.location.host ? undefined : ref.href
  } catch {
    return undefined
  }
}

/** Call once per page load, before anything navigates. */
export function captureVisit() {
  const url = new URL(window.location.href)
  const touch: Touch = { landing_path: url.pathname + url.search, seen_at: new Date().toISOString() }
  let tagged = false
  for (const tag of TAGS) {
    const value = url.searchParams.get(tag)?.trim()
    if (value) {
      touch[tag] = value
      tagged = true
    }
  }
  const referrer = externalReferrer()
  if (referrer) touch.referrer = referrer

  if (!read(FIRST_KEY)) write(FIRST_KEY, touch)
  // An untagged, unreferred visit (typed URL, bookmark, reload) doesn't erase the campaign that brought them.
  if (tagged || referrer || !read(LAST_KEY)) write(LAST_KEY, touch)
}

function deviceType(): DeviceType {
  const ua = navigator.userAgent
  // iPadOS reports itself as a Mac; touch points give it away.
  if (/iPad|Tablet/i.test(ua) || (/Android/i.test(ua) && !/Mobile/i.test(ua)) || (/Macintosh/.test(ua) && navigator.maxTouchPoints > 1)) {
    return 'TABLET'
  }
  return /Mobi|iPhone|Android/i.test(ua) ? 'MOBILE' : 'DESKTOP'
}

/** The `acquisition` object every sign-up, sign-in and booking request sends. */
export function acquisition(choices: SignupChoices = {}): Acquisition {
  const note = choices.heard_from === 'OTHER' ? choices.heard_from_note?.trim() : undefined
  return {
    first_touch: read(FIRST_KEY),
    last_touch: read(LAST_KEY),
    device_type: deviceType(),
    ...choices,
    heard_from_note: note || undefined,
  }
}

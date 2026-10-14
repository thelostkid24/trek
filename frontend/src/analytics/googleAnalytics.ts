import { getConsent, onConsentChange } from './consent.ts'

// Google Analytics 4 (docs/TRD.md §7.18): page views, scrolls and clicks, in totals, read at analytics.google.com.
// Loads only after the visitor accepts the cookie banner, and only in a production build, so local work doesn't
// show up as visits. Page changes are counted by GA's own history tracking ("enhanced measurement").

const MEASUREMENT_ID = 'G-TXKW03B3E2'

type Gtag = (...args: unknown[]) => void
declare global {
  interface Window {
    dataLayer?: unknown[]
    gtag?: Gtag
  }
}

let loaded = false

function load() {
  if (loaded) {
    window.gtag?.('consent', 'update', { analytics_storage: 'granted' })
    return
  }
  loaded = true
  window.dataLayer = window.dataLayer ?? []
  window.gtag = function gtag() {
    // gtag.js reads the `arguments` object itself, not an array.
    // eslint-disable-next-line prefer-rest-params
    window.dataLayer!.push(arguments)
  }
  // Measurement only: nothing for ads, ever.
  window.gtag('consent', 'default', {
    analytics_storage: 'granted',
    ad_storage: 'denied',
    ad_user_data: 'denied',
    ad_personalization: 'denied',
  })
  window.gtag('js', new Date())
  window.gtag('config', MEASUREMENT_ID, { allow_google_signals: false, allow_ad_personalization_signals: false })

  const script = document.createElement('script')
  script.async = true
  script.src = `https://www.googletagmanager.com/gtag/js?id=${MEASUREMENT_ID}`
  document.head.appendChild(script)
}

/** Stops counting and deletes GA's cookies (_ga, _ga_<id>), which it sets on the parent domain. */
function stop() {
  window.gtag?.('consent', 'update', { analytics_storage: 'denied' })
  const host = window.location.hostname
  const domains = ['', host, `.${host.split('.').slice(-2).join('.')}`]
  for (const cookie of document.cookie.split(';')) {
    const name = cookie.split('=')[0].trim()
    if (name !== '_ga' && !name.startsWith('_ga_')) continue
    for (const domain of domains) {
      document.cookie = `${name}=; Max-Age=0; path=/${domain ? `; domain=${domain}` : ''}`
    }
  }
}

/** Call once per page load. */
export function startGoogleAnalytics() {
  if (!import.meta.env.PROD) return
  if (getConsent() === 'granted') load()
  onConsentChange(() => (getConsent() === 'granted' ? load() : stop()))
}

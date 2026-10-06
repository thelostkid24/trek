import { Link } from 'react-router-dom'
import { setConsent, useConsent } from '../analytics/consent.ts'
import { SITE_LINKS } from '../lib/siteLinks.ts'

/**
 * Asks once whether we may remember the link a visitor arrived by (docs/TRD.md §7.18). Both answers are equally easy,
 * nothing is remembered until "Accept", and the page stays usable while it's open: it's a bar, not a dialog.
 * The answer can be changed on /cookies.
 */
export function CookieBanner() {
  const consent = useConsent()
  if (consent) return null

  const button = 'rounded-full px-5 py-2 text-sm font-medium transition'
  return (
    <section
      aria-label="Cookie choices"
      className="fixed inset-x-3 bottom-3 z-40 rounded-2xl border border-paper-300 bg-paper-50 p-4 text-sm text-stone-700 shadow-lg sm:inset-x-auto sm:right-5 sm:bottom-5 sm:max-w-md sm:p-5"
    >
      <p className="leading-relaxed">
        We use one cookie to keep you signed in. With your OK, we’ll also remember the link you came in by, so we know
        which channels bring trekkers. No ads, no third-party trackers.{' '}
        <Link to={SITE_LINKS.cookies} className="font-medium text-pine-700 underline hover:text-pine-600">
          Cookie policy
        </Link>
      </p>
      <div className="mt-3 flex gap-2">
        <button type="button" onClick={() => setConsent('granted')} className={`${button} bg-stone-900 text-paper-50 hover:bg-stone-700`}>
          Accept
        </button>
        <button
          type="button"
          onClick={() => setConsent('denied')}
          className={`${button} border border-stone-300 text-stone-900 hover:border-stone-500`}
        >
          No thanks
        </button>
      </div>
    </section>
  )
}

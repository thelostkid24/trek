import { Link } from 'react-router-dom'
import { Seo } from '../components/Seo.tsx'

/**
 * Shown when a page throws while rendering or its code fails to load (docs/TRD.md §7.0). The router renders it under
 * the site's header and footer, so visitors can still get around; reloading picks up a fresh copy of the page.
 */
export function ErrorPage() {
  return (
    <section className="min-h-[calc(100dvh-4rem)] bg-paper-50 px-4 py-20 text-center font-plex">
      <Seo title="Something went wrong" noindex />
      <h1 className="font-serif text-3xl font-light tracking-tight text-ink-900">Something went wrong</h1>
      <p className="mt-2 text-sm text-ink-700">This page ran into a problem. Reloading usually fixes it.</p>
      <div className="mt-6 flex items-center justify-center gap-5">
        <button
          type="button"
          onClick={() => window.location.reload()}
          className="rounded-full bg-ink-950 px-5 py-2.5 text-sm font-medium text-white hover:bg-pine-700"
        >
          Reload page
        </button>
        <Link to="/" className="text-sm font-medium text-pine-600 hover:text-pine-700">
          Back home
        </Link>
      </div>
    </section>
  )
}

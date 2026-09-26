import { Link } from 'react-router-dom'

export function NotFoundPage() {
  return (
    <section className="min-h-[calc(100dvh-4rem)] bg-paper-50 px-4 py-20 text-center font-plex">
      <h1 className="font-serif text-3xl font-light tracking-tight text-ink-900">Page not found</h1>
      <p className="mt-2 text-sm text-ink-700">The page you’re looking for doesn’t exist or has moved.</p>
      <Link to="/" className="mt-5 inline-block text-sm font-medium text-pine-600 hover:text-pine-700">
        Back home
      </Link>
    </section>
  )
}

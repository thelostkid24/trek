import { Link, Outlet } from 'react-router-dom'

export function Layout() {
  return (
    <div className="flex min-h-dvh flex-col">
      <header className="sticky top-0 z-10 border-b border-stone-200 bg-white/90 backdrop-blur">
        <div className="mx-auto flex h-14 max-w-5xl items-center justify-between px-4">
          <Link to="/" className="text-lg font-semibold tracking-tight text-brand-900">
            Sahyātri
          </Link>
          <nav className="text-sm text-stone-600">{/* Nav links added per feature */}</nav>
        </div>
      </header>

      <main className="mx-auto w-full max-w-5xl flex-1 px-4 py-6">
        <Outlet />
      </main>

      <footer className="border-t border-stone-200 bg-white">
        <div className="mx-auto max-w-5xl px-4 py-4 text-xs text-stone-500">
          © {new Date().getFullYear()} Sahyātri · Fair-trade, small-batch treks
        </div>
      </footer>
    </div>
  )
}

import type { router as appRouter } from '../router.tsx'

/**
 * Keeps a long-open tab from running an old release. Every deploy renames the app's script
 * (/assets/index-<hash>.js), so a fresh copy of index.html naming a different one means a newer release is out.
 * We check when the tab comes back into view and on page changes (at most every five minutes), and once a
 * newer release is seen, the next page change loads it with a full page load instead of a client-side one.
 * Dev builds have no hashed script, so this does nothing there.
 */
const CHECK_EVERY_MS = 5 * 60_000
const RELOADED_KEY = 'tev.chunk_reload'

const current = document.querySelector<HTMLScriptElement>('script[type="module"][src*="/assets/index-"]')?.getAttribute('src')
let stale = false
let lastCheck = Date.now()

async function check() {
  if (!current || stale || Date.now() - lastCheck < CHECK_EVERY_MS) return
  lastCheck = Date.now()
  try {
    const html = await (await fetch('/', { cache: 'no-store' })).text()
    const latest = html.match(/\/assets\/index-[\w-]+\.js/)?.[0]
    if (latest && latest !== current) stale = true
  } catch {
    // Offline or a blip: try again on the next check.
  }
}

export function watchForNewRelease(router: typeof appRouter) {
  // Pages outside the main bundle load on first visit (router.tsx). A deploy deletes the old release's files, so a
  // tab opened before it fails to load them: reload into the new release instead, at most once a minute so a
  // genuinely missing file can't loop.
  window.addEventListener('vite:preloadError', (event) => {
    try {
      const last = Number(sessionStorage.getItem(RELOADED_KEY) ?? 0)
      if (Date.now() - last < 60_000) return
      sessionStorage.setItem(RELOADED_KEY, String(Date.now()))
    } catch {
      // Storage blocked: reloading anyway is better than a broken page.
    }
    event.preventDefault()
    window.location.reload()
  })
  if (!current) return
  let path = router.state.location.pathname
  router.subscribe((state) => {
    if (state.navigation.state !== 'idle' || state.location.pathname === path) return
    path = state.location.pathname
    // The URL already shows the new page; reloading it fetches the new release for that page.
    if (stale) window.location.reload()
    else void check()
  })
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') void check()
  })
}

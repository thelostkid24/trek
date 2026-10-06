import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router-dom'
import { captureVisit } from './analytics/attribution.ts'
import { AuthProvider } from './auth/AuthProvider.tsx'
// The typeface ships with the site rather than coming from Google Fonts, so visitors' browsers only talk to us.
import '@fontsource-variable/plus-jakarta-sans/wght.css'
import '@fontsource-variable/plus-jakarta-sans/wght-italic.css'
import './index.css'
import { watchForNewRelease } from './lib/freshness.ts'
import { router } from './router.tsx'

// devMock.ts is a local-only, gitignored dev tool; import.meta.glob keeps this a
// no-op when the file isn't present (e.g. on a fresh clone or in CI). The app renders
// after it loads, so no first request slips through to a real backend. Dev server only:
// a production build never bundles it, even when built from a checkout that has the file.
const devMock = import.meta.env.DEV ? import.meta.glob('./devMock.ts')['./devMock.ts'] : undefined
const ready = devMock ? devMock() : Promise.resolve()

// index.html's link-preview tags are for clients that don't run JavaScript. Each page's <Seo> takes over from here,
// and leaving them would mean two titles and two sets of og: tags.
for (const el of document.querySelectorAll('head [data-static-head]')) el.remove()

// Before the router can rewrite the URL and drop the campaign tags.
captureVisit()
// A tab left open picks up a new release on its next page change.
watchForNewRelease(router)

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: false } },
})

void ready.then(() =>
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <RouterProvider router={router} />
        </AuthProvider>
      </QueryClientProvider>
    </StrictMode>,
  ),
)

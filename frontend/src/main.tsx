import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router-dom'
import { AuthProvider } from './auth/AuthProvider.tsx'
import './index.css'
import { router } from './router.tsx'

// devMock.ts is a local-only, gitignored dev tool; import.meta.glob keeps this a
// no-op when the file isn't present (e.g. on a fresh clone or in CI).
const devMock = import.meta.glob('./devMock.ts')['./devMock.ts']
if (devMock) void devMock()

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: false } },
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <RouterProvider router={router} />
      </AuthProvider>
    </QueryClientProvider>
  </StrictMode>,
)

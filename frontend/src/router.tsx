import type { ComponentType } from 'react'
import { createBrowserRouter, Navigate } from 'react-router-dom'
import { RequireAuth } from './auth/RequireAuth.tsx'
import { PROFILE_PATH } from './auth/useCompleteSignIn.ts'
import { SITE_LINKS } from './lib/siteLinks.ts'
import { Layout } from './components/Layout.tsx'
import { Private } from './components/Seo.tsx'
import { DepartureDetailPage } from './pages/DepartureDetailPage.tsx'
import { GuidePage } from './pages/GuidePage.tsx'
import { GuidesPage } from './pages/GuidesPage.tsx'
import { HomePage } from './pages/HomePage.tsx'
import {
  CancellationsPage,
  ContactPage,
  CookiesPage,
  CreditsPage,
  FaqsPage,
  PrivacyPage,
  TermsPage,
  VisionPage,
} from './pages/InfoPages.tsx'
import { NotFoundPage } from './pages/NotFoundPage.tsx'
import { TreksPage } from './pages/TreksPage.tsx'
import { TrekPage } from './pages/TrekPage.tsx'

/**
 * Pages most visitors never open (sign-in, checkout, account, guide console, admin) load on first visit instead of
 * shipping in the main bundle, which keeps the public pages light on phones. `name` is the page's export.
 * A chunk from a replaced release that fails to load triggers a reload (lib/freshness.ts).
 */
function page<K extends string>(load: () => Promise<Record<K, ComponentType>>, name: K, title?: string) {
  return async () => {
    const Page: ComponentType = (await load())[name]
    return title ? { element: <Private title={title}><Page /></Private> } : { Component: Page }
  }
}

export const router = createBrowserRouter([
  {
    element: <Layout />,
    // A direct visit to a lazy page renders nothing for the moment its code takes to arrive.
    HydrateFallback: () => null,
    children: [
      { path: '/', element: <HomePage /> },
      { path: '/login', lazy: page(() => import('./pages/LoginPage.tsx'), 'LoginPage', 'Log in') },
      { path: '/signup', lazy: page(() => import('./pages/SignupPage.tsx'), 'SignupPage', 'Sign up') },
      { path: '/forgot-password', lazy: page(() => import('./pages/PasswordResetPages.tsx'), 'ForgotPasswordPage', 'Forgot password') },
      { path: '/reset-password', lazy: page(() => import('./pages/PasswordResetPages.tsx'), 'ResetPasswordPage', 'Reset password') },
      // Trekker account pages share the account layout (name, then pill tabs).
      {
        lazy: async () => {
          const { AccountLayout } = await import('./pages/trekker/AccountLayout.tsx')
          return {
            element: (
              <Private title="My account">
                <RequireAuth role="TREKKER">
                  <AccountLayout />
                </RequireAuth>
              </Private>
            ),
          }
        },
        children: [
          { path: '/account', element: <Navigate to="/account/bookings" replace /> },
          { path: PROFILE_PATH, lazy: page(() => import('./pages/trekker/ProfilePage.tsx'), 'ProfilePage') },
          { path: '/account/bookings', lazy: page(() => import('./pages/trekker/BookingsPage.tsx'), 'BookingsPage') },
          { path: '/account/bookings/:id', lazy: page(() => import('./pages/trekker/BookingDetailPage.tsx'), 'BookingDetailPage') },
          { path: '/account/gear', lazy: page(() => import('./pages/trekker/GearPage.tsx'), 'GearPage') },
        ],
      },
      { path: '/account/verify-email', lazy: page(() => import('./pages/VerifyEmailPage.tsx'), 'VerifyEmailPage', 'Verify email') },
      { path: '/treks', element: <TreksPage /> },
      { path: '/treks/:slug', element: <TrekPage /> },
      { path: '/departures/:id', element: <DepartureDetailPage /> },
      { path: '/guides', element: <GuidesPage /> },
      { path: '/guides/:id', element: <GuidePage /> },
      { path: '/blog', lazy: page(() => import('./pages/BlogPages.tsx'), 'BlogPage') },
      { path: '/blog/:slug', lazy: page(() => import('./pages/BlogPages.tsx'), 'BlogPostPage') },
      { path: SITE_LINKS.faqs, element: <FaqsPage /> },
      { path: SITE_LINKS.cancellations, element: <CancellationsPage /> },
      { path: SITE_LINKS.vision, element: <VisionPage /> },
      { path: SITE_LINKS.contact, element: <ContactPage /> },
      { path: SITE_LINKS.terms, element: <TermsPage /> },
      { path: SITE_LINKS.privacy, element: <PrivacyPage /> },
      { path: SITE_LINKS.cookies, element: <CookiesPage /> },
      { path: SITE_LINKS.credits, element: <CreditsPage /> },
      // Public: guest checkout needs no account (docs/TRD.md §7.6).
      { path: '/book/:departureId', lazy: page(() => import('./pages/trekker/BookPage.tsx'), 'BookPage', 'Book') },
      {
        path: '/admin',
        lazy: async () => {
          const { AdminLayout } = await import('./pages/admin/AdminLayout.tsx')
          return {
            element: (
              <Private title="Admin">
                <RequireAuth role="ADMIN">
                  <AdminLayout />
                </RequireAuth>
              </Private>
            ),
          }
        },
        children: [
          { index: true, lazy: page(() => import('./pages/admin/DeparturesAdminPage.tsx'), 'DeparturesAdminPage') },
          { path: 'tracks', lazy: page(() => import('./pages/admin/TracksAdminPage.tsx'), 'TracksAdminPage') },
          { path: 'content', lazy: page(() => import('./pages/admin/ContentAdminPage.tsx'), 'ContentAdminPage') },
          { path: 'guides', lazy: page(() => import('./pages/admin/GuidesAdminPage.tsx'), 'GuidesAdminPage') },
          { path: 'blog', lazy: page(() => import('./pages/admin/BlogAdminPage.tsx'), 'BlogAdminPage') },
          { path: 'insights', lazy: page(() => import('./pages/admin/InsightsPage.tsx'), 'InsightsPage') },
        ],
      },
      {
        path: '/guide',
        lazy: async () => {
          const { GuideReportsPage } = await import('./pages/guide/GuideReportsPage.tsx')
          return {
            element: (
              <Private title="Guide reports">
                <RequireAuth role="GUIDE">
                  <GuideReportsPage />
                </RequireAuth>
              </Private>
            ),
          }
        },
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
])

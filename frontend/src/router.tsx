import { createBrowserRouter, Navigate } from 'react-router-dom'
import { RequireAuth } from './auth/RequireAuth.tsx'
import { PROFILE_PATH } from './auth/useCompleteSignIn.ts'
import { SITE_LINKS } from './lib/siteLinks.ts'
import { Layout } from './components/Layout.tsx'
import { AdminLayout } from './pages/admin/AdminLayout.tsx'
import { ContentAdminPage } from './pages/admin/ContentAdminPage.tsx'
import { DeparturesAdminPage } from './pages/admin/DeparturesAdminPage.tsx'
import { GuidesAdminPage } from './pages/admin/GuidesAdminPage.tsx'
import { InsightsPage } from './pages/admin/InsightsPage.tsx'
import { TracksAdminPage } from './pages/admin/TracksAdminPage.tsx'
import { DepartureDetailPage } from './pages/DepartureDetailPage.tsx'
import { GuidePage } from './pages/GuidePage.tsx'
import { GuideReportsPage } from './pages/guide/GuideReportsPage.tsx'
import { HomePage } from './pages/HomePage.tsx'
import {
  CancellationsPage,
  ContactPage,
  FaqsPage,
  PrivacyPage,
  TermsPage,
  VisionPage,
} from './pages/InfoPages.tsx'
import { LoginPage } from './pages/LoginPage.tsx'
import { NotFoundPage } from './pages/NotFoundPage.tsx'
import { SignupPage } from './pages/SignupPage.tsx'
import { TreksPage } from './pages/TreksPage.tsx'
import { TrekPage } from './pages/TrekPage.tsx'
import { AccountLayout } from './pages/trekker/AccountLayout.tsx'
import { BookingDetailPage } from './pages/trekker/BookingDetailPage.tsx'
import { BookingsPage } from './pages/trekker/BookingsPage.tsx'
import { BookPage } from './pages/trekker/BookPage.tsx'
import { GearPage } from './pages/trekker/GearPage.tsx'
import { ProfilePage } from './pages/trekker/ProfilePage.tsx'
import { VerifyEmailPage } from './pages/VerifyEmailPage.tsx'

export const router = createBrowserRouter([
  {
    element: <Layout />,
    children: [
      { path: '/', element: <HomePage /> },
      { path: '/login', element: <LoginPage /> },
      { path: '/signup', element: <SignupPage /> },
      // Trekker account pages share the sidebar layout (the handle tells Layout to keep the footer clear of it).
      {
        handle: { accountSidebar: true },
        element: (
          <RequireAuth role="TREKKER">
            <AccountLayout />
          </RequireAuth>
        ),
        children: [
          { path: '/account', element: <Navigate to="/account/bookings" replace /> },
          { path: PROFILE_PATH, element: <ProfilePage /> },
          { path: '/account/bookings', element: <BookingsPage /> },
          { path: '/account/bookings/:id', element: <BookingDetailPage /> },
          { path: '/account/gear', element: <GearPage /> },
        ],
      },
      { path: '/account/verify-email', element: <VerifyEmailPage /> },
      { path: '/treks', element: <TreksPage /> },
      { path: '/treks/:slug', element: <TrekPage /> },
      { path: '/departures/:id', element: <DepartureDetailPage /> },
      { path: '/guides/:id', element: <GuidePage /> },
      { path: SITE_LINKS.faqs, element: <FaqsPage /> },
      { path: SITE_LINKS.cancellations, element: <CancellationsPage /> },
      { path: SITE_LINKS.vision, element: <VisionPage /> },
      { path: SITE_LINKS.contact, element: <ContactPage /> },
      { path: SITE_LINKS.terms, element: <TermsPage /> },
      { path: SITE_LINKS.privacy, element: <PrivacyPage /> },
      // Public: guest checkout needs no account (docs/TRD.md §7.6).
      { path: '/book/:departureId', element: <BookPage /> },
      {
        path: '/admin',
        element: (
          <RequireAuth role="ADMIN">
            <AdminLayout />
          </RequireAuth>
        ),
        children: [
          { index: true, element: <DeparturesAdminPage /> },
          { path: 'tracks', element: <TracksAdminPage /> },
          { path: 'content', element: <ContentAdminPage /> },
          { path: 'guides', element: <GuidesAdminPage /> },
          { path: 'insights', element: <InsightsPage /> },
        ],
      },
      {
        path: '/guide',
        element: (
          <RequireAuth role="GUIDE">
            <GuideReportsPage />
          </RequireAuth>
        ),
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
])

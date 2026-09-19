import { createBrowserRouter } from 'react-router-dom'
import { RequireAuth } from './auth/RequireAuth.tsx'
import { PROFILE_PATH } from './auth/useCompleteSignIn.ts'
import { SITE_LINKS } from './lib/siteLinks.ts'
import { Layout } from './components/Layout.tsx'
import { AdminLayout } from './pages/admin/AdminLayout.tsx'
import { DeparturesAdminPage } from './pages/admin/DeparturesAdminPage.tsx'
import { GuidesAdminPage } from './pages/admin/GuidesAdminPage.tsx'
import { TracksAdminPage } from './pages/admin/TracksAdminPage.tsx'
import { DepartureDetailPage } from './pages/DepartureDetailPage.tsx'
import { GuidePage } from './pages/GuidePage.tsx'
import { HomePage } from './pages/HomePage.tsx'
import { CancellationsPage, ContactPage, FaqsPage, LeadATrekPage, VisionPage } from './pages/InfoPages.tsx'
import { LoginPage } from './pages/LoginPage.tsx'
import { NotFoundPage } from './pages/NotFoundPage.tsx'
import { SignupPage } from './pages/SignupPage.tsx'
import { TrekPage } from './pages/TrekPage.tsx'
import { AccountLayout } from './pages/trekker/AccountLayout.tsx'
import { BookingDetailPage } from './pages/trekker/BookingDetailPage.tsx'
import { BookingsPage } from './pages/trekker/BookingsPage.tsx'
import { BookPage } from './pages/trekker/BookPage.tsx'
import { ProfilePage } from './pages/trekker/ProfilePage.tsx'
import { VerifyEmailPage } from './pages/VerifyEmailPage.tsx'

export const router = createBrowserRouter([
  {
    element: <Layout />,
    children: [
      { path: '/', element: <HomePage /> },
      { path: '/login', element: <LoginPage /> },
      { path: '/signup', element: <SignupPage /> },
      // Trekker account pages share the sidebar layout.
      {
        element: (
          <RequireAuth role="TREKKER">
            <AccountLayout />
          </RequireAuth>
        ),
        children: [
          { path: PROFILE_PATH, element: <ProfilePage /> },
          { path: '/account/bookings', element: <BookingsPage /> },
          { path: '/account/bookings/:id', element: <BookingDetailPage /> },
        ],
      },
      { path: '/account/verify-email', element: <VerifyEmailPage /> },
      { path: '/treks/:slug', element: <TrekPage /> },
      { path: '/departures/:id', element: <DepartureDetailPage /> },
      { path: '/guides/:id', element: <GuidePage /> },
      { path: SITE_LINKS.faqs, element: <FaqsPage /> },
      { path: SITE_LINKS.cancellations, element: <CancellationsPage /> },
      { path: SITE_LINKS.vision, element: <VisionPage /> },
      { path: SITE_LINKS.contact, element: <ContactPage /> },
      { path: SITE_LINKS.leadATrek, element: <LeadATrekPage /> },
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
          { path: 'guides', element: <GuidesAdminPage /> },
        ],
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
])

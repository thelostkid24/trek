# Frontend guide

The frontend is a React single-page app (`frontend/`) that talks only to the backend API (`docs/API.md`). In production it's a static build served from S3 through CloudFront, on the same domain as the API.

## Stack
| Piece | Choice |
|---|---|
| UI | React 19, TypeScript |
| Build / dev server | Vite (`tsc -b && vite build`) |
| Styling | Tailwind CSS v4; brand tokens in `src/index.css` `@theme` |
| Routing | React Router v7 (`createBrowserRouter`) |
| Server state | TanStack Query; no global client store |
| Lint | oxlint |
| Payments | Razorpay Checkout script, loaded on demand |
| Sign-in | Google Identity Services button |

## Run it
```bash
npm --prefix frontend install
npm --prefix frontend run dev        # http://localhost:5173, API at VITE_API_BASE_URL
npm --prefix frontend run build      # type-check + production build into frontend/dist
npm --prefix frontend run lint
```

| Env var | Dev | Production |
|---|---|---|
| `VITE_API_BASE_URL` | `http://localhost:8081` | empty (same origin; CloudFront sends `/api/*` to the backend) |
| `VITE_GOOGLE_CLIENT_ID` | your OAuth client ID | same, set as a GitHub variable for the deploy build |

Vite bakes these in at build time, so changing them needs a rebuild.

## Code layout
| Folder | What's in it |
|---|---|
| `src/api/` | `client.ts` (`apiFetch`, `ApiError`) plus one typed file per backend area: `auth`, `account`, `profile`, `catalog`, `bookings`, `payments`, `admin` |
| `src/auth/` | Session state (`AuthProvider`, `useAuth`, `RequireAuth`), post-sign-in redirect, error copy (`errorMessages.ts`), form validation rules |
| `src/components/` | Shared UI: `Layout` (header, footer), `Avatar`, `FaqList`, `Ridgeline` art, `Reveal`; subfolders per area: `auth`, `booking`, `catalog`, `profile`, `admin` |
| `src/pages/` | One component per route; trekker pages in `pages/trekker`, admin pages in `pages/admin` |
| `src/lib/` | Plain helpers and data: `format` (money, dates), `razorpay` (Checkout loader), `siteLinks`, `faqs`, `business` (company details), `trek`, `photos`, `reveal` |
| `src/router.tsx` | Every route |
| `src/index.css` | Tailwind import and brand tokens |

## Routes
| Path | Page | Who |
|---|---|---|
| `/` | `HomePage`: hero, how it works, upcoming departures with month filter, FAQ | anyone |
| `/treks/:slug` | `TrekPage`: trek facts, itinerary, photos, every departure | anyone |
| `/departures/:id` | `DepartureDetailPage`: one departure, guide, seats, Book | anyone |
| `/guides/:id` | `GuidePage`: guide profile and their departures | anyone |
| `/book/:departureId` | `BookPage`: seats, contact, hold and pay (guest or signed in) | anyone |
| `/login`, `/signup` | Email/password, phone OTP, Google | anyone |
| `/account/verify-email` | Confirms the emailed link | anyone |
| `/account/profile` | `ProfilePage`: profile, photo, security | trekker |
| `/account/bookings` | `BookingsPage`: upcoming and past trips | trekker |
| `/account/bookings/:id` | `BookingDetailPage`: status, pay, travellers, cancel | trekker |
| `/admin/departures`, `/admin/tracks`, `/admin/guides` | Catalog admin | admin |
| `/faqs`, `/cancellations`, `/vision`, `/contact`, `/lead-a-trek`, `/terms`, `/privacy` | Info pages (`InfoPages.tsx`) | anyone |
| `*` | `NotFoundPage` | anyone |

`RequireAuth role="TREKKER" | "ADMIN"` guards the account and admin areas and sends visitors to `/login`.

## Talking to the API
- Every call goes through `apiFetch` in `src/api/client.ts`. It sends JSON (or `FormData` as-is), adds `Authorization: Bearer`, and throws `ApiError` with the backend's `status`, `code`, `message` and `details`.
- Signed-in calls go through `withAuth` from `useAuth()`. It passes the current access token and, on `TOKEN_EXPIRED`, refreshes once and retries.
- Wrap reads in TanStack Query (`useQuery`) and writes in `useMutation`, then invalidate the affected queries.
- Show errors with `messageFor(error)` and field errors with `fieldErrors(error)` from `auth/errorMessages.ts`. When the backend adds an error code, add its wording there.

## Sessions
- The access token is kept **in memory only**, never in localStorage, so it dies with the tab.
- The refresh token is an httpOnly cookie the page can't read. On load, `AuthProvider` calls `POST /api/auth/refresh` to restore the session.
- Every sign-in endpoint returns the same `AuthResponse`; pass it to `setSession`.
- Guest checkout signs the visitor in as a guest account. Guest notices ask them to verify a phone or email to keep access.

## Booking and payment in the browser
1. `BookPage` holds seats (`POST /api/public/bookings` for visitors, `/api/trekker/bookings` when signed in).
2. `usePayForBooking` creates the Razorpay order, loads Checkout through `lib/razorpay.ts` and opens it with the returned key, order and prefill.
3. On success it calls `verify`. If the modal is dismissed or payment fails, it polls the payment, because the webhook may already have confirmed it.
4. The booking page shows a countdown until the 10-minute hold ends.

## Styling
- Mobile first: design at about 375 px, then enhance with `sm:` and `md:`.
- Use the brand tokens (`brand-*`, `laterite-*`, `ink-*`, `paper-*`, `pine-*`) rather than raw colours. Fonts: `font-serif` (Spectral) for headings, `font-plex` (IBM Plex Sans) for page text, `font-display` (Fraunces) on the landing page, `font-sans` (Inter) as the default.
- Pages own their container width; `Layout` gives a full-bleed `<main>`.

## Before launch
- Fill in `src/lib/business.ts` (legal name, address, support email and phone, hours, grievance officer). The Contact, Terms, Privacy and Vision pages read from it.
- `CancellationsPage` repeats the backend refund tiers (`app.bookings.refund-tiers`); keep the two in step.

## Adding a page
1. Add the API call and its types to the matching `src/api/*.ts` file.
2. Create the page in `src/pages/` (or `pages/trekker`, `pages/admin`) and use `useQuery` / `useMutation` through `withAuth` where sign-in is needed.
3. Register the route in `src/router.tsx`, wrapped in `RequireAuth` if it's private.
4. Run `npm --prefix frontend run build` and `npm --prefix frontend run lint`.
5. Add the screen to the feature's section in `docs/TRD.md`.

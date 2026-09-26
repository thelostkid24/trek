# The Empty Valley — Technical Requirement Document (living)

The product is **The Empty Valley**. The codebase keeps its working name, Sahyātri (`com.sahyatri`, database `sahyatri`).

**Companion to:** PRD v1.2 (guide-led execution) and TRD v1.2 (lifecycle/protocol deltas).
**How this doc works:** this file is the source of truth for how the system is built. It starts with only the foundation. Each feature, when built, adds its schema, endpoints and screens to §5 and §6. Nothing is designed here ahead of the feature that needs it.

---

## 1. Stack

| Layer | Choice |
|---|---|
| Backend | Java 21, Spring Boot 4.1 (Maven wrapper), Spring Web MVC, Spring Data JPA, Bean Validation, Spring Security |
| Database | PostgreSQL 16 (Docker Compose locally), schema managed only by Flyway (`ddl-auto: validate`) |
| Backend tests | JUnit 5, MockMvc, Testcontainers (real Postgres, never H2) |
| Frontend | React 19, TypeScript, Vite, Tailwind CSS v4, React Router, TanStack Query |
| Auth (planned) | Email + password, phone OTP, Google OAuth — all issue the same JWT |
| Payments (planned) | Razorpay Standard Checkout — UPI, cards, netbanking, wallets (test mode in dev); design in §7.4 |
| Hosting | AWS ap-south-1: CloudFront (+WAF) → S3 (SPA) and ALB → ECS Fargate (backend container), RDS PostgreSQL 16, S3 uploads, SES email, MSG91 SMS. Runbook: `docs/DEPLOY.md` |
| Background work | Spring `@Scheduled` only (`@EnableScheduling` on `BackendApplication`) — no job-queue service in V1. Jobs take row locks and run one transaction per row. |

## 2. Repo layout

```
trek/
├── docker-compose.yml        # postgres
├── .env.example              # every env var the system reads
├── docs/TRD.md               # this file
├── backend/
│   └── src/main/java/com/sahyatri/
│       ├── common/
│       │   ├── config/       # @ConfigurationProperties records, CORS
│       │   ├── security/     # filter chain, JWT beans, JSON 401/403, refresh cookie
│       │   ├── exception/    # ApiError, ApiException, GlobalExceptionHandler
│       │   ├── util/         # stateless helpers (HashingUtils)
│       │   ├── storage/      # FileStorage (local disk in dev), public file serving
│       │   ├── audit/        # AuditLog (append-only audit_events)
│       │   ├── web/          # shared response wrappers (ItemsResponse), RateLimitFilter
│       │   ├── mail/         # MailTransport: log (dev) or SES (prod)
│       │   └── health/
│       └── <feature>/        # e.g. auth/
│           ├── controller/
│           ├── service/
│           ├── repository/
│           ├── entity/       # JPA entities + their enums
│           ├── dto/          # request/response records, one per file
│           └── <adapter>/    # feature-specific integrations (auth/sms, account/mail)
│   └── src/main/resources/db/migration/   # V<n>__<feature>.sql
└── frontend/
    └── src/
        ├── api/              # fetch client + per-feature API functions
        ├── auth/             # session state (AuthProvider, useAuth)
        ├── components/       # shared UI (Layout, …)
        └── pages/            # route components, grouped per role as they appear
```

## 3. Conventions

### 3.1 Backend
- **Feature, then layer**: `com.sahyatri.<feature>.{controller,service,repository,entity,dto}`. Controllers stay thin (validate, call service, shape HTTP); services own logic and ownership checks; DTOs are Java records, one per file. Cross-feature infrastructure goes in `common.{config,security,exception,util}`, never inside a feature.
- **Route prefixes by audience**:
  - `/api/public/**` — no auth
  - `/api/auth/**` — login/signup flows (plus the public email-verify link)
  - `/api/account/**` — the signed-in user's own account (any role): photo, email, phone, password
  - `/api/trekker/**`, `/api/guide/**`, `/api/admin/**` — role-guarded
  - `/api/webhooks/**` — signature-verified, no JWT
- **JSON is snake_case** (global Jackson naming strategy). Java stays camelCase.
- **Errors**: always `{ "code": "UPPER_SNAKE", "message": "...", "details": {} }`. Services throw `ApiException`; `GlobalExceptionHandler` renders it. Validation failures → `400 VALIDATION_FAILED` with `details.fields`.
- **Money** is integer **paise** (`long` / `BIGINT`), fields suffixed `_paise`. Percentages are basis points (`_bps`).
- **Time**: `TIMESTAMPTZ` / `Instant` for moments, `DATE` / `LocalDate` for trek dates.
- **IDs**: UUID.
- **Ownership checks live in the service layer**, never only in the controller or UI.
- **Migrations**: one Flyway file per feature (`V1__auth.sql`, …). Never edit a migration that has been applied; add a new one.
- **State changes** that matter for audit (publish, cancel, refunds, payouts) write an audit record (table defined when first needed).

### 3.2 Frontend
- Mobile-first: design at ~375px, enhance with `sm:`/`md:` breakpoints.
- All server calls go through `src/api/client.ts` (`apiFetch`), which throws `ApiError` carrying the backend's `code`.
- Server state via TanStack Query; no global client store unless a feature needs one.
- Brand tokens in `src/index.css` `@theme`.

## 4. Security scope (V1)
1. **Role guards + ownership checks** — every non-public route requires a JWT with the right role; services verify the caller owns the resource.
2. **Webhook signature verification** — Razorpay webhooks verified via HMAC before any processing; processing is idempotent. A Checkout success reported by the browser is re-verified server-side (order|payment HMAC) before anything is marked paid. Card data never touches our servers (§7.4).
3. **Secrets only via environment variables** — listed in `.env.example`, never committed. In production they come from AWS Secrets Manager.
4. **Rate limits** — WAF at the edge (per-IP flood rule, managed rule sets) and `RateLimitFilter` in the app (§7.8), plus the per-email login lockout and per-phone OTP throttles (§7.2).

> Current state: JWT + role guards are live (§7.2). Account endpoints (§7.3) accept any signed-in role; ownership comes from the token subject, never the request.

## 5. Product laws the code must enforce
Carried from PRD/TRD v1.2. These are rules, not schema. Each feature that touches them must encode them (DB constraint where possible, else service layer + test).

1. **A paid booking is unconditional.** No code path cancels a paid booking for low fill.
2. **Small batch**: a departure's `max_group_size` ≤ 10 (one guide); seats sold never exceed it.
3. **Only published departures are bookable.** Publishing happens only after the guide has acknowledged the current track protocol version.
4. **Force majeure is the only path by which we cancel a departure** (weather, permit denied, guide incapacity with no substitute, safety). It requires a stored reason and triggers a full refund of every paid booking. Separately, a trekker may cancel their own booking under the refund policy (§7.6); that never cancels the departure.
5. **Inspection happens at T-2/T-3, never T-0.** A failed item leads to remediation, then a substitute guide. It never leads to cancellation.
6. **No money moves at publish.** Guide draws are capped at collected guide-share minus prior draws. Settlement only after completion with no open remediation or force-majeure flag.
7. **Guide share is configuration**, frozen on the departure at publish.
8. **Guide ranking is computed at read time**, never stored.
9. **A published departure with zero bookings by its start date expires/archives.** It is not a cancellation.

## 6. Schema
Defined per feature. Each feature appends a subsection: tables/columns added, constraints, migration filename.

### 6.1 Auth — `V1__auth.sql`
- **`users`** — `id UUID PK`, `full_name TEXT NULL`, `email TEXT UNIQUE NULL` (stored lowercased), `phone TEXT UNIQUE NULL` (E.164), `password_hash TEXT NULL` (bcrypt), `google_subject TEXT UNIQUE NULL`, `role TEXT NOT NULL CHECK IN ('TREKKER','GUIDE','ADMIN')`, `status TEXT NOT NULL CHECK IN ('ACTIVE','DISABLED')`, `email_verified_at TIMESTAMPTZ NULL`, `phone_verified_at TIMESTAMPTZ NULL`, `created_at`, `updated_at TIMESTAMPTZ NOT NULL`.
  CHECK: at least one of `email`, `phone`, `google_subject` is not null (dropped in V5 so guest-checkout accounts can exist, §6.5).
- **`refresh_tokens`** — `id UUID PK`, `user_id UUID FK → users`, `token_hash TEXT UNIQUE NOT NULL` (SHA-256 of the opaque token; raw token never stored), `expires_at`, `revoked_at NULL`, `replaced_by_id UUID NULL`, `created_at`. Presenting a revoked token revokes every live token of that user (reuse detection).
- **`otp_challenges`** — `id UUID PK`, `phone TEXT NOT NULL`, `code_hash TEXT NOT NULL`, `expires_at`, `attempts INT NOT NULL DEFAULT 0`, `consumed_at NULL`, `created_at`. Index `(phone, created_at)`.

### 6.2 Trekker profile & account — `V2__trekker_profile.sql`
- **`trekker_profiles`** — `user_id UUID PK FK → users ON DELETE CASCADE`, `date_of_birth DATE NULL`, `gender TEXT NULL CHECK IN ('FEMALE','MALE','NON_BINARY','PREFER_NOT_TO_SAY')`, `home_city TEXT NULL`, `experience_level TEXT NULL CHECK IN ('BEGINNER','INTERMEDIATE','EXPERIENCED')`, `bio TEXT NULL`, `emergency_name`, `emergency_relation`, `emergency_phone TEXT NULL` (CHECK: all three null or all three set), `blood_group TEXT NULL CHECK IN ('A+','A-','B+','B-','AB+','AB-','O+','O-')`, `medical_notes TEXT NULL`, `created_at`, `updated_at TIMESTAMPTZ NOT NULL`. Row is created on first save. `full_name` stays on `users`.
- **`users`** — adds `avatar_key TEXT NULL` (storage key of the current photo; a new key per upload).
- **`email_verifications`** — `id UUID PK`, `user_id UUID FK → users ON DELETE CASCADE`, `email TEXT NOT NULL` (lowercased), `token_hash TEXT UNIQUE NOT NULL` (SHA-256), `expires_at`, `consumed_at NULL`, `created_at`. Index `(user_id, created_at)`.
- **`otp_challenges`** — adds `purpose TEXT NOT NULL DEFAULT 'LOGIN' CHECK IN ('LOGIN','PHONE_CHANGE')`; index becomes `(phone, purpose, created_at)`.

### 6.3 Catalog — `V3__catalog.sql`
- **`tracks`** — `id UUID PK`, `slug TEXT UNIQUE NOT NULL` (`^[a-z0-9]+(-[a-z0-9]+)*$`), `name TEXT NOT NULL`, `region TEXT NOT NULL`, `difficulty TEXT NOT NULL CHECK IN ('EASY','MODERATE','CHALLENGING')` (`'EASY_MODERATE'` since V10), `duration_days INT NOT NULL CHECK 1..7`, `max_altitude_m INT NULL CHECK > 0`, `summary TEXT NOT NULL` (≤ 200), `description TEXT NOT NULL`, `meeting_point TEXT NOT NULL`, `created_at`, `updated_at`.
- **`departures`** — `id UUID PK`, `track_id UUID FK → tracks`, `guide_id UUID FK → users`, `start_date DATE NOT NULL`, `end_date DATE NOT NULL` (= start + duration − 1; CHECK `end_date >= start_date`), `price_paise BIGINT NOT NULL CHECK > 0` (per seat), `max_group_size INT NOT NULL CHECK 1..6` (1..10 since V5), `seats_taken INT NOT NULL DEFAULT 0` (CHECK `0 ≤ seats_taken ≤ max_group_size` — the database backstop for law 2), `status TEXT NOT NULL CHECK IN ('DRAFT','PUBLISHED','CANCELLED','EXPIRED','COMPLETED')`, `guide_share_bps INT NULL CHECK 0..10000` (CHECK: set unless `DRAFT`), `published_at TIMESTAMPTZ NULL`, `cancelled_at TIMESTAMPTZ NULL`, `cancel_reason_code TEXT NULL CHECK IN ('WEATHER','PERMIT_DENIED','GUIDE_UNAVAILABLE','SAFETY')`, `cancel_reason_note TEXT NULL` (CHECK: `CANCELLED` ⇔ `cancelled_at` and `cancel_reason_code` set), `created_at`, `updated_at`. Index `(status, start_date)`.
- **`audit_events`** — `id UUID PK`, `actor_id UUID NULL FK → users` (null = system job), `action TEXT NOT NULL` (e.g. `DEPARTURE_PUBLISHED`), `entity_type TEXT NOT NULL`, `entity_id UUID NOT NULL`, `data JSONB NOT NULL DEFAULT '{}'`, `created_at`. Index `(entity_type, entity_id, created_at)`. Append-only.

### 6.4 Bookings & payments — `V4__bookings_payments.sql`
- **`bookings`** — `id UUID PK`, `user_id UUID FK → users`, `departure_id UUID FK → departures`, `seats INT NOT NULL CHECK 1..6` (1..10 since V5), `price_paise_per_seat BIGINT NOT NULL CHECK > 0`, `amount_paise BIGINT NOT NULL` (CHECK `= price_paise_per_seat × seats`; both frozen at hold time), `status TEXT NOT NULL CHECK IN ('HELD','CONFIRMED','EXPIRED','RELEASED','CANCELLED_BY_TREKKER','CANCELLED_FORCE_MAJEURE')`, `hold_expires_at TIMESTAMPTZ NOT NULL`, `confirmed_at TIMESTAMPTZ NULL`, `refund_policy JSONB NULL` (tiers frozen at confirmation), `cancelled_at TIMESTAMPTZ NULL`, `created_at`, `updated_at`.
  CHECKs: `confirmed_at` and `refund_policy` are set exactly when the status is `CONFIRMED` or a `CANCELLED_*`; `cancelled_at` is set exactly when the status is a `CANCELLED_*`.
  Partial unique index `(departure_id, user_id) WHERE status IN ('HELD','CONFIRMED')` — one live booking per trekker per departure. Indexes `(status, hold_expires_at)`, `(user_id, created_at)`, `(departure_id, status)`.
- **`booking_travellers`** — `id UUID PK`, `booking_id UUID FK → bookings ON DELETE CASCADE`, `position INT NOT NULL CHECK 0..5` (0..9 since V5) (0 = the booker), `full_name TEXT NOT NULL`, `phone TEXT NULL`, `date_of_birth DATE NOT NULL`, `gender TEXT NOT NULL CHECK IN ('FEMALE','MALE','NON_BINARY','PREFER_NOT_TO_SAY')`. `UNIQUE (booking_id, position)`.
- **`payments`** — as designed in §7.4, with `booking_id UUID NOT NULL FK → bookings`. No unique index on `booking_id`: a second capture for the same booking (rare) is kept as `PAID` and refunded in full. Indexes `(booking_id)`, `(status, created_at)`.
- **`payment_refunds`** — as §7.4, plus `kind TEXT NOT NULL CHECK IN ('TREKKER_CANCELLATION','FORCE_MAJEURE','LATE_CAPTURE')`. Index `(status, created_at)`, `(payment_id)`.
- **`webhook_events`** — as §7.4.
- **`departures.seats_taken`** counts seats of `HELD` and `CONFIRMED` bookings. It is only changed under the departure row lock.

### 6.5 Guest checkout & groups of 10 — `V5__guest_checkout.sql`
- **`users`** — drops `users_has_identity`. A row with no `email`, `phone` or `google_subject` is a **guest** (created by guest checkout). Verifying a phone or email through §7.3 turns it into a normal account.
- **`departures.max_group_size`** CHECK 1..10; **`bookings.seats`** CHECK 1..10; **`booking_travellers.position`** CHECK 0..9 (law 2).
- **`bookings`** — adds `contact_name`, `contact_phone` (WhatsApp, E.164), `contact_email` (lowercased), all `TEXT NULL`: who we reach about the booking. Always set for new bookings; existing rows backfilled from the account.

### 6.7 Trekker profile details — `V7__trekker_profile_details.sql`
- **`trekker_profiles`** — adds `height_cm INT NULL CHECK 100..250`, `weight_kg INT NULL CHECK 25..250`, `diet TEXT NULL CHECK IN ('VEGETARIAN','EGGETARIAN','NON_VEGETARIAN','VEGAN','JAIN')`, `allergies TEXT NULL`, `highest_altitude_m INT NULL CHECK 0..8849`, `shoe_size_uk INT NULL CHECK 1..15` (for gear rental).

### 6.6 Trek page — `V6__trek_page.sql`
- **`tracks`** — adds route facts, all `NULL` until an admin fills them in: `distance_km NUMERIC(5,1) CHECK > 0` (on foot, start to finish), `base_altitude_m INT CHECK > 0` (trailhead; with `max_altitude_m` gives the altitude gain), `highest_camp_m INT CHECK > 0`, `stay TEXT` (e.g. "Tents · twin share"), `season_label TEXT` (e.g. "Snow trek · Dec–Apr").
- **`track_itinerary_days`** — `id UUID PK`, `track_id UUID FK → tracks ON DELETE CASCADE`, `day_number INT NOT NULL CHECK 1..7`, `summary TEXT NOT NULL`. `UNIQUE (track_id, day_number)`. Empty, or exactly one row per day of `duration_days` (service rule).

### 6.8 Trek photos — `V8__track_photos.sql`
- **`track_photos`** — `id UUID PK` (also the storage key `track-photos/<id>.jpg`), `track_id UUID FK → tracks ON DELETE CASCADE`, `caption TEXT NULL CHECK length 1..200`, `created_at TIMESTAMPTZ NOT NULL`. Index `(track_id, created_at)`. At most 30 per track (service rule).

### 6.9 Trek catalog — `V9__track_catalog.sql`
- **`tracks`** — adds `listed BOOLEAN NOT NULL DEFAULT FALSE`: show this track in the public catalog even when it has no upcoming dates. A track with an upcoming published departure is in the catalog whatever `listed` says.

### 6.10 Full trek page — `V10__trek_content.sql`
- **`tracks`** — `difficulty` may also be `EASY_MODERATE` ("Easy to moderate"). Adds `pickup_drop TEXT NULL` ("Sankri to Sankri"), `cloakroom BOOLEAN NULL`, `offloading BOOLEAN NULL` (paid bag offloading), `offloading_price_paise BIGINT NULL CHECK > 0` (null with offloading = paid, price not fixed yet). NULL = not stated; the page leaves the fact out.
- **`track_itinerary_days`** — `summary` stays the day's heading. Adds `description TEXT`, `distance_km NUMERIC(4,1) CHECK > 0`, `start_altitude_m`, `high_altitude_m`, `end_altitude_m INT CHECK > 0`, `hours_min NUMERIC(3,1) CHECK > 0`, `hours_max NUMERIC(3,1)` (CHECK: null, or ≥ `hours_min`), `route_note TEXT` ("mostly downhill"). All nullable.
- **`track_photos`** — adds `place TEXT CHECK length 1..100` ("Kedarkantha summit") and `day_number INT CHECK 1..7`.
- **`trek_content_items`** — `id UUID PK`, `track_id UUID NULL FK → tracks ON DELETE CASCADE` (NULL = shown on every trek), `kind TEXT NOT NULL CHECK IN ('INCLUDED','NOT_INCLUDED','SAFETY','SAFETY_CALLOUT','SAFETY_NOTE','FAQ','WHY_US')`, `position INT NOT NULL CHECK ≥ 0`, `badge TEXT NULL`, `title TEXT NULL`, `body TEXT NOT NULL`, `created_at`. `UNIQUE NULLS NOT DISTINCT (track_id, kind, position)`.
- **`snow_reports`** — `id UUID PK`, `track_id UUID FK → tracks ON DELETE CASCADE`, `reported_by UUID FK → users`, `reported_on DATE NOT NULL`, `reported_from TEXT NOT NULL`, `snowline_m INT CHECK > 0`, `night_temp_c INT CHECK -60..50`, `conditions JSONB NOT NULL DEFAULT '[]'` (`[{label, value}]`), `crowd_place TEXT`, `crowd_tents INT CHECK ≥ 0` (CHECK: both or neither), `note TEXT`, `has_photo BOOLEAN NOT NULL DEFAULT false` (file at `snow-reports/<id>.jpg`), `created_at`. Index `(track_id, reported_on DESC, created_at DESC)`. Append-only.
- **`guide_profiles`** — `user_id UUID PK FK → users ON DELETE CASCADE`, `leading_since INT CHECK 1950..2100`, `languages TEXT`, `certification TEXT`, `certification_number TEXT`, `quote TEXT`, `created_at`, `updated_at`. Row created on first save.
- **`reviews`** — `id UUID PK`, `booking_id UUID UNIQUE FK → bookings`, `user_id`, `departure_id`, `guide_id`, `track_id` (FKs), `rating INT NOT NULL CHECK 1..5`, `body TEXT NULL CHECK length 1..2000`, `author_name TEXT NOT NULL` (first name from the booking), `created_at`, `updated_at`. Index `(guide_id, created_at DESC)`.

### 6.11 Acquisition, consent, last seen — `V11__acquisition.sql`
NULL everywhere = not captured (rows made before V11, or nothing sent). Tracking values come from URLs we don't control, so the server cleans them rather than rejecting the request: trims, drops control characters, truncates to the column limit, lower-cases UTM tags, keeps only `http(s)` referrers and landing paths starting with `/`, and turns a future `seen_at` into now.
- **`users`** — first touch, written once when the account is created and never rewritten: `signup_method TEXT CHECK IN ('EMAIL','PHONE','GOOGLE','GUEST_CHECKOUT')`, `utm_source`, `utm_medium`, `utm_campaign`, `utm_term`, `utm_content TEXT CHECK length 1..200`, `gclid`, `fbclid TEXT CHECK length 1..500`, `referrer TEXT CHECK length 1..1000`, `landing_path TEXT CHECK length 1..500`, `first_seen_at TIMESTAMPTZ`, `device_type TEXT CHECK IN ('MOBILE','TABLET','DESKTOP')`, `heard_from TEXT CHECK IN ('INSTAGRAM','YOUTUBE','GOOGLE_SEARCH','FRIEND_FAMILY','WHATSAPP_GROUP','BLOG_FORUM','OTHER')`, `heard_from_note TEXT CHECK length 1..200`. Also `last_seen_at TIMESTAMPTZ` (sign-in and token refresh, at most hourly), `marketing_email_consent_at`, `marketing_whatsapp_consent_at TIMESTAMPTZ` (NULL = no consent; every change is also an `audit_events` row). Index `(created_at)`.
- **`bookings`** — last touch, the visit that led to the booking: the same UTM, click-id, `referrer`, `landing_path` and `device_type` columns, plus `touch_seen_at TIMESTAMPTZ`. Partial index `(confirmed_at) WHERE confirmed_at IS NOT NULL`.

## 7. Feature log
Each feature appends: scope, endpoints, tables, screens, tests.

### 7.0 Foundation
- `GET /api/public/health` → `{ status, database, server_time }`
- Global error shape, CORS for `http://localhost:5173`, permit-all security (temporary).
- Frontend: layout shell, home page showing backend health, 404 page.
- Tests: `HealthControllerTests` (health + 404 error shape against Testcontainers Postgres).

### 7.1 Landing page
- Frontend only, no endpoints or tables.
- `HomePage`: illustrated hero (`components/Ridgeline.tsx`, pure SVG), promise stats, how it works, departures with month filter, guarantee comparison, guides pitch, FAQ, closing CTA.
- Departures were static sample data until §7.5; the section now reads `GET /api/public/departures` (`components/catalog/DepartureCard.tsx`).
- Hero photo: `frontend/public/hero.jpg` (static asset, covers the ridgeline art; the art shows if the file is missing).
- `Layout`: nav anchors, full-bleed `<main>` (pages own their container), backend health indicator moved to the footer.
- Brand tokens: added brand 200–950 and `laterite` accent; display font Fraunces (Google Fonts) alongside Inter.

### 7.2 Auth (sign up / log in)
**Status:** contract agreed; backend implemented (`com.sahyatri.auth`). Frontend builds against this.

**Scope:** email + password, phone OTP, Google sign-in. All three return the same `AuthResponse`. Public sign-up always creates a `TREKKER`; guides and admins are onboarded separately (later feature).

#### Tokens
- **Access token**: JWT (HS256), 15 min, returned in the body. Send as `Authorization: Bearer <access_token>`. Claims: `sub` = user id, `role`.
- **Refresh token**: opaque, 30 days, **httpOnly cookie** — never visible to JS:
  `sahyatri_refresh=<token>; HttpOnly; SameSite=Lax; Path=/api/auth; Max-Age=2592000` (`Secure` when `AUTH_COOKIE_SECURE=true`).
  Rotated on every `/refresh`; old one becomes invalid. Grace: a token rotated < 30 s ago still refreshes (two tabs / React StrictMode refreshing at once). Reusing a token rotated longer ago revokes every session of that user. Tokens revoked by logout are rejected outright.
- Frontend calls every `/api/auth/**` endpoint with `credentials: 'include'`. Backend CORS allows credentials for the frontend origin.
- Keep the access token in memory only. On page load call `POST /api/auth/refresh` to restore the session. On `401 TOKEN_EXPIRED` from any call, refresh once and retry; if refresh fails, treat as logged out.

#### Shapes
```jsonc
// AuthResponse — signup, login, otp/verify, google, refresh
{
  "access_token": "eyJhbGciOi...",
  "token_type": "Bearer",
  "expires_in": 900,          // seconds
  "is_new_user": false,       // true only when this call created the account
  "user": { /* User */ }
}

// User
{
  "id": "5f1c...-uuid",
  "full_name": "Asha Rao",            // null possible for phone-OTP sign-ups
  "email": "asha@example.com",        // nullable
  "phone": "+919876543210",           // nullable, E.164
  "role": "TREKKER",                  // TREKKER | GUIDE | ADMIN
  "email_verified": true,
  "phone_verified": false,
  "guest": false,                    // true = guest checkout, no email/phone/Google yet (§7.6)
  "auth_methods": ["PASSWORD"],       // any of PASSWORD | PHONE_OTP | GOOGLE
  "created_at": "2026-09-15T10:00:00Z"
}
```

#### Endpoints
| Method & path | Auth | Request body | Success | Errors |
|---|---|---|---|---|
| `POST /api/auth/signup` | — | `{ full_name, email, password }` | `201` AuthResponse + cookie | `400 VALIDATION_FAILED`, `409 EMAIL_ALREADY_REGISTERED` |
| `POST /api/auth/login` | — | `{ email, password }` | `200` AuthResponse + cookie | `400 VALIDATION_FAILED`, `401 INVALID_CREDENTIALS`, `403 ACCOUNT_DISABLED`, `429 TOO_MANY_ATTEMPTS` |
| `POST /api/auth/otp/request` | — | `{ phone }` | `202 { expires_in: 300, resend_after: 30 }` | `400 VALIDATION_FAILED`, `429 OTP_RATE_LIMITED` (`details.retry_after` seconds) |
| `POST /api/auth/otp/verify` | — | `{ phone, code, full_name? }` | `200` AuthResponse + cookie | `400 VALIDATION_FAILED`, `400 OTP_INVALID` (`details.attempts_left`), `410 OTP_EXPIRED`, `429 OTP_TOO_MANY_ATTEMPTS`, `403 ACCOUNT_DISABLED` |
| `POST /api/auth/google` | — | `{ id_token }` (Google Identity Services credential) | `200` AuthResponse + cookie | `400 VALIDATION_FAILED`, `401 GOOGLE_TOKEN_INVALID`, `403 ACCOUNT_DISABLED` |
| `POST /api/auth/refresh` | refresh cookie | — | `200` AuthResponse + rotated cookie | `401 REFRESH_TOKEN_INVALID` |
| `POST /api/auth/logout` | refresh cookie (optional) | — | `204`, cookie cleared | — |
| `GET /api/auth/me` | Bearer | — | `200` User | `401 UNAUTHENTICATED`, `401 TOKEN_EXPIRED` |

All errors use the global `{ code, message, details }` shape. `VALIDATION_FAILED` puts per-field messages in `details.fields`, e.g. `{ "fields": { "password": "must contain a letter and a digit" } }`.

#### Validation
| Field | Rule |
|---|---|
| `email` | valid email, ≤ 254 chars; trimmed and lowercased server-side |
| `password` | 8–72 chars, at least one letter and one digit |
| `full_name` | 1–100 chars after trim |
| `phone` | E.164; V1 accepts Indian mobiles only: `+91` followed by 10 digits starting 6–9 |
| `code` | exactly 6 digits |
| `id_token` | non-blank |

#### Behaviour the UI can rely on
- Login never reveals whether an email exists — wrong email and wrong password both give `INVALID_CREDENTIALS`. 5 failures per email in 15 min → `TOO_MANY_ATTEMPTS`.
- OTP: 6 digits, valid 5 min, 5 verify attempts per code, resend allowed after 30 s, max 5 requests/hour per phone. Requesting a new code invalidates the previous one.
- OTP verify for an unknown phone creates a TREKKER (`is_new_user: true`). If `full_name` was not sent, the user has `full_name: null` — the UI sends them to the profile page (§7.3) to fill it in.
- Google: if the Google email is verified and matches an existing account, Google is linked to that account (`auth_methods` gains `GOOGLE`); otherwise a new TREKKER is created with `email_verified: true`.
- Email sign-up does not send a verification email automatically (`email_verified: false`); the trekker verifies from the profile page (§7.3).
- Dev only: OTP codes are not sent by SMS; the backend logs them.

#### Frontend
- Types + functions: `frontend/src/api/auth.ts`.
- `src/auth/`: `AuthProvider` keeps the access token in a ref (memory only) and calls `/refresh` on load; `useAuth()` exposes `status` (`loading | anonymous | authenticated`), `user`, `setSession`, `signOut`, and `withAuth(token => call)` which retries once after `TOKEN_EXPIRED`. **Later features make authenticated calls through `withAuth`.** `errorMessages.ts` maps error codes to copy.
- Screens: `/login` and `/signup` (`pages/LoginPage.tsx`, `pages/SignupPage.tsx`) with Email / Mobile tabs and Google on top (hidden unless `VITE_GOOGLE_CLIENT_ID` is set). Pieces in `components/auth/`. After sign-in the user returns to `location.state.from` (same-origin paths only), else `/`.
- Header shows "Sign in" or first name + "Sign out".

#### Backend
- `auth/controller/AuthController` → `auth/service/`: `AuthService` (flows), `TokenService` (HS256 access JWT, refresh rotation), `OtpService`, `GoogleTokenVerifier` (Google JWKS, checks `iss` + `aud` = `GOOGLE_CLIENT_ID`), `LoginAttemptLimiter` (in-memory, single instance).
- `auth/entity/` (`User`, `RefreshToken`, `OtpChallenge`, enums), `auth/repository/`, `auth/dto/`, `auth/sms/` (`SmsSender`, `LoggingSmsSender` in V1).
- `common/security/`: `SecurityConfig`, `JwtConfig` (encoder/decoder, bcrypt), `RefreshCookie`, `JsonAuthenticationEntryPoint`, `JsonAccessDeniedHandler`. `common/config/`: `AuthProperties`, `CorsConfig`. `common/util/HashingUtils`.
- Refresh tokens stored as SHA-256; OTP codes stored as HMAC-SHA256 keyed by `JWT_SECRET`; passwords bcrypt.
- `SecurityConfig`: `/api/public/**`, `/api/auth/**` public except `/api/auth/me`; `/api/trekker|guide|admin/**` require matching `role` claim; everything else authenticated. 401/403 from the security layer use the standard error shape (`UNAUTHENTICATED`, `TOKEN_EXPIRED`, `FORBIDDEN`). CORS allows credentials.
- `GlobalExceptionHandler` now reports `details.fields` keys in snake_case.
- Config `app.auth.*`: `JWT_SECRET` (≥ 32 bytes, startup fails otherwise; dev default in `application.yml`), `AUTH_COOKIE_SECURE`, `GOOGLE_CLIENT_ID` (Google sign-in returns `GOOGLE_TOKEN_INVALID` until set).
- Tests (Testcontainers): `AuthControllerTests` (signup/login/throttle/me/expired token/role guard/refresh rotation, grace, reuse detection/logout), `OtpFlowTests` (create + re-login, rate limit, attempts lockout, expiry, validation), `GoogleAuthTests` (create, link by verified email, no link when unverified, bad token).

### 7.3 Trekker profile & account settings
**Status:** contract agreed; backend (`com.sahyatri.profile`, `com.sahyatri.account`) and frontend implemented.

**Scope:** a trekker views and edits their profile (personal basics, trek experience and highest altitude, emergency contact, health & fitness, diet and rental shoe size), uploads a photo, verifies or changes their email (link), adds or changes their phone (OTP), and sets or changes their password. Profile data is trekker-only (`/api/trekker/profile`); photo, email, phone and password are account-level (`/api/account/**`, any role) so guides reuse them later.

#### Shapes
```jsonc
// User (§7.2) gains:
{ "avatar_url": "http://localhost:8081/api/public/files/avatars/3f2a….jpg" }   // null when no photo

// TrekkerProfile — GET and PUT response
{
  "full_name": "Asha Rao",            // PUT: required, 1–100 after trim (stored on users)
  "avatar_url": null,                 // read-only; change via /api/account/avatar
  "date_of_birth": "1995-04-12",      // nullable; age must be 18–100
  "gender": "FEMALE",                 // FEMALE | MALE | NON_BINARY | PREFER_NOT_TO_SAY | null
  "home_city": "Pune",                // nullable, ≤ 100
  "experience_level": "BEGINNER",     // BEGINNER | INTERMEDIATE | EXPERIENCED | null
  "highest_altitude_m": 3800,         // nullable integer, 0–8849
  "bio": "Weekend trekker…",          // nullable, ≤ 500
  "emergency_contact": {              // null, or all three fields
    "name": "Meera Rao",              // 1–100
    "relation": "Sister",             // 1–50
    "phone": "+919812345678"          // Indian mobile (same rule as §7.2), not the trekker's own phone
  },
  "height_cm": 162,                   // nullable integer, 100–250
  "weight_kg": 58,                    // nullable integer, 25–250
  "blood_group": "O+",                // A+ A- B+ B- AB+ AB- O+ O- | null
  "allergies": "Peanuts",             // nullable, ≤ 300
  "medical_notes": "Mild asthma",     // nullable, ≤ 1000; visible to the trekker only (guide access comes later)
  "diet": "VEGETARIAN",               // VEGETARIAN | EGGETARIAN | NON_VEGETARIAN | VEGAN | JAIN | null
  "shoe_size_uk": 6,                  // nullable integer, 1–15 (gear rental)
  "completion": {                     // read-only
    "percent": 60,
    "missing": ["date_of_birth", "emergency_contact"]
  },
  "updated_at": "2026-09-16T10:00:00Z" // read-only; null until first save
}
```
`PUT` takes the editable fields above (everything except `avatar_url`, `completion`, `updated_at`) and **replaces** the profile: omitted or `null` fields are cleared. Blank strings are stored as `null`.

`completion.missing` keys, in order: `full_name`, `avatar`, `date_of_birth`, `gender`, `home_city`, `experience_level`, `emergency_contact`, `blood_group`, `height_weight` (both set), `diet`, `phone_verified`, `email_verified`. `percent` = share of those present, rounded down. `bio`, `highest_altitude_m`, `allergies`, `medical_notes` and `shoe_size_uk` are optional and don't count.

#### Endpoints
| Method & path | Auth | Request body | Success | Errors |
|---|---|---|---|---|
| `GET /api/trekker/profile` | Bearer, TREKKER | — | `200` TrekkerProfile (empty fields if never saved) | `401`, `403 FORBIDDEN` |
| `PUT /api/trekker/profile` | Bearer, TREKKER | TrekkerProfile (editable fields) | `200` TrekkerProfile | `400 VALIDATION_FAILED` (nested keys like `emergency_contact.phone`) |
| `PUT /api/account/avatar` | Bearer | `multipart/form-data`, part `file` | `200` User | `400 UNSUPPORTED_IMAGE`, `413 FILE_TOO_LARGE` |
| `DELETE /api/account/avatar` | Bearer | — | `200` User | — |
| `GET /api/public/files/avatars/{key}.jpg` | — | — | `200 image/jpeg`, `Cache-Control: public, max-age=31536000, immutable` | `404 NOT_FOUND` |
| `POST /api/account/email` | Bearer | `{ email }` | `202 { expires_in: 86400 }` — verification link emailed to `email` | `400 VALIDATION_FAILED`, `409 EMAIL_ALREADY_REGISTERED`, `409 EMAIL_ALREADY_VERIFIED`, `429 EMAIL_RATE_LIMITED` (`details.retry_after`) |
| `POST /api/auth/email/verify` | — (the token is the proof) | `{ token }` | `200 { email }` | `400 VALIDATION_FAILED`, `400 EMAIL_TOKEN_INVALID`, `410 EMAIL_TOKEN_EXPIRED`, `409 EMAIL_ALREADY_REGISTERED` |
| `POST /api/account/phone/otp` | Bearer | `{ phone }` | `202 { expires_in: 300, resend_after: 30 }` | `400 VALIDATION_FAILED`, `409 PHONE_ALREADY_REGISTERED`, `409 PHONE_ALREADY_VERIFIED`, `429 OTP_RATE_LIMITED` |
| `POST /api/account/phone/verify` | Bearer | `{ phone, code }` | `200` User | `400 VALIDATION_FAILED`, `400 OTP_INVALID`, `410 OTP_EXPIRED`, `429 OTP_TOO_MANY_ATTEMPTS`, `409 PHONE_ALREADY_REGISTERED` |
| `PUT /api/account/password` | Bearer | `{ current_password?, new_password }` | `200` AuthResponse (`is_new_user: false`) + rotated refresh cookie | `400 VALIDATION_FAILED`, `400 CURRENT_PASSWORD_INCORRECT`, `400 EMAIL_REQUIRED`, `429 TOO_MANY_ATTEMPTS` |

Like `/api/auth/me`, account and profile endpoints return `401 UNAUTHENTICATED` when the account no longer exists or is disabled. Call `PUT /api/account/password` with `credentials: 'include'` so the browser stores the new cookie.

#### Behaviour the UI can rely on
- **Photo:** JPEG or PNG, ≤ 5 MB. The server checks the actual bytes (not the filename or header), centre-crops and re-encodes to a 512×512 JPEG (which strips EXIF/GPS), and returns a new `avatar_url` each time. The previous file is deleted.
- **Email:** `POST /api/account/email` with the *current* unverified address sends a link that verifies it; with a new address it sends a link that, when opened, switches the account to that address (verified) and emails a notice to the old one. The account email doesn't change until the link is opened. The link is `${FRONTEND_BASE_URL}/account/verify-email?token=…`, valid 24 h, single use; a newer request invalidates older links. Max 5 requests per hour per user. `EMAIL_ALREADY_VERIFIED` = the address is already this account's verified email. Opening the link doesn't require being signed in.
- **Phone:** codes follow the §7.2 OTP rules (validity, attempts, resend, hourly cap) but are purpose-scoped: a sign-in code can't attach a phone and a phone-change code can't sign in. Success sets the phone as verified, so `auth_methods` gains `PHONE_OTP`. `PHONE_ALREADY_VERIFIED` = the number is already this account's verified phone.
- **Password:** `current_password` is required (and checked) only if the account already has a password; `new_password` follows the §7.2 rule. Setting a password needs an email on the account (`EMAIL_REQUIRED`), because password sign-in is by email. 5 wrong `current_password` in 15 min → `TOO_MANY_ATTEMPTS`. Success revokes **every** existing session and returns a fresh one (`auth_methods` gains `PASSWORD` when set for the first time).
- Changing the name, photo or phone changes `User`; the UI should update its in-memory user from the response (or from `full_name` in the profile response).
- Dev only: emails are not sent; the backend logs them, including the verification link.

#### Frontend
- `api/profile.ts` (profile types + calls) and `api/account.ts` (photo, email, phone, password); `api/client.ts` passes `FormData` bodies through untouched. All authenticated calls go through `withAuth`.
- `pages/trekker/AccountLayout.tsx` wraps `/account`, `/account/profile`, `/account/bookings(/:id)` and `/account/gear` in the account sidebar (My treks · My profile · Gear, §7.10; `/account` redirects to `/account/bookings`) (a tab strip on phones). Its `account-area` class sets the `--field-*` CSS variables that `TextField` and `components/profile/fields.tsx` read, so fields there use the paper style.
- `auth/`: `RequireAuth` route guard (redirects to `/login` with `state.from`, optional role); `AuthProvider` exposes `updateUser(user)`. After OTP sign-up without a name, sign-in lands on the profile page.
- Screens: `/account/profile` (TREKKER only, `pages/trekker/ProfilePage.tsx`): header with photo, name and completion; one form for personal / experience / emergency contact / health, saved with a single `PUT`; a "Sign-in & security" section for email, phone and password. The Health card shows a BMI worked out live from height and weight (WHO bands; not stored). The form no longer shows "About your trekking": `bio` stays in the API and is sent back unchanged. `/account/verify-email` (`pages/VerifyEmailPage.tsx`) consumes the link. Pieces in `components/profile/`.
- Header: the account name links to the profile and shows the photo or initials.

#### Backend
- `profile/`: `TrekkerProfileController` → `TrekkerProfileService` (upsert, completion, own-phone check), `TrekkerProfile` entity + `Gender`, `ExperienceLevel`, `BloodGroup`, DTOs.
- `account/`: `AccountController`, `EmailVerificationController` (`/api/auth/email/verify`); services `AvatarService`, `EmailChangeService`, `PhoneChangeService`, `PasswordService`; `EmailVerification` entity; `mail/EmailSender` with `LoggingEmailSender` in V1.
- `common/storage/`: `FileStorage`, `LocalFileStorage` (`UPLOAD_DIR`), `PublicFileController`. `common/config/AppProperties`: `PUBLIC_BASE_URL` (builds `avatar_url`), `FRONTEND_BASE_URL` (email links), `UPLOAD_DIR`, `MAIL_FROM`.
- `OtpService` takes an `OtpPurpose`. `GlobalExceptionHandler` maps oversized uploads to `413 FILE_TOO_LARGE`. Multipart limit 5 MB.
- Tests (Testcontainers): `TrekkerProfileTests`, `AvatarTests`, `EmailChangeTests`, `PhoneChangeTests`, `PasswordChangeTests`.

### 7.4 Payments (Razorpay): design
**Status:** built with bookings (§7.6). Tables are in §6.4. Where this section and §7.6 differ, §7.6 wins; the differences are listed under "Changes when built" below.

**Scope:** trekkers pay through one **Razorpay Standard Checkout**. The methods offered are:
- `upi`: collect or intent, and QR on desktop.
- `card`: RuPay, Visa, Mastercard and Amex, domestic only in V1.
- `netbanking`: the trekker signs in to their own bank.
- `wallet`

EMI, cardless EMI and Pay Later are hidden in Checkout (`config.display.hide`) and turned off on the Razorpay dashboard. V1 doesn't save cards.

**Card data:** card details are entered only inside Razorpay's Checkout. They never reach our servers, logs or database, which keeps us in the smallest PCI-DSS scope (SAQ-A). RBI card tokenisation is Razorpay's responsibility.

#### Flow
1. **Create order:** the frontend calls `POST /api/trekker/payments/orders` with `{ booking_id }`.
   - The server works out `amount_paise` from the booking and never accepts an amount from the client.
   - It creates a Razorpay Order (`amount`, `currency: "INR"`, `receipt` = our payment id, `notes.booking_id`) and stores a `payments` row with status `CREATED`.
   - It returns the `PaymentOrder` shape below.
2. **Checkout:** the frontend loads `https://checkout.razorpay.com/v1/checkout.js` and opens it with `key`, `order_id`, `amount`, `currency`, `prefill` and `theme.color` (brand).
3. **Verify (fast path):** on success, Checkout's handler receives `razorpay_payment_id`, `razorpay_order_id` and `razorpay_signature`, and the frontend calls `POST /api/trekker/payments/{id}/verify`.
   - The server computes `hex(HMAC_SHA256(order_id + "|" + payment_id, RAZORPAY_KEY_SECRET))` and compares it in constant time.
   - It then fetches the payment from Razorpay to record `method` and `method_detail`, and marks the row `PAID`.
4. **Webhook (source of truth):** Razorpay calls `POST /api/webhooks/razorpay`.
   - `X-Razorpay-Signature` is checked as the HMAC-SHA256 of the **raw request body**, keyed with `RAZORPAY_WEBHOOK_SECRET`, before any parsing.
   - Handled events: `payment.captured`, `order.paid`, `payment.failed`, `refund.processed`, `refund.failed`.
   - Processing is idempotent: each `x-razorpay-event-id` is stored once, and a repeat returns `200` without doing anything.
5. **One state change:** verify and webhook both go through the same service method under a row lock (`SELECT … FOR UPDATE` on the payment). Whichever arrives second finds the payment already `PAID` and does nothing.
6. **Auto-capture** is on (dashboard setting), so there is no separate `AUTHORIZED` state to manage.

**Payment states:**
- A payment starts as `CREATED` and ends as `PAID`, `FAILED` or `EXPIRED`.
- Refunds don't change the status: `amount_refunded_paise` tracks them.
- A `payment.failed` event keeps the order open, because Razorpay lets the trekker retry inside the same Checkout. It only records `failure_code` and `failure_reason`.

**Reconciler:** a `@Scheduled` job looks at payments still `CREATED` after `PAYMENT_ORDER_TTL` (30 min) and fetches their order from Razorpay.
- If a captured payment exists, the payment becomes `PAID` (it catches a missed webhook).
- Otherwise it becomes `EXPIRED`.
- Bookings will use this to release a held seat (§5 law 2).

**Refunds:**
- A force-majeure cancellation (§5 law 4) refunds every `PAID` booking in full through the Razorpay Refunds API. It writes a `payment_refunds` row and an audit record (§3.1).
- Refund status is finalised by the `refund.processed` and `refund.failed` webhooks.
- UI copy: refunds reach cards and netbanking in about 5–7 working days; UPI and wallets are usually faster.

#### Shapes
```jsonc
// PaymentOrder — create-order response
{
  "payment_id": "8b1e…-uuid",
  "key_id": "rzp_test_…",               // public key only; the secret never leaves the server
  "razorpay_order_id": "order_…",
  "amount_paise": 649900,
  "currency": "INR",
  "prefill": { "name": "Asha Rao", "email": "asha@example.com", "contact": "+919876543210" }  // nullable fields
}

// Payment — verify and GET response
{
  "id": "8b1e…-uuid",
  "booking_id": "…-uuid",
  "status": "PAID",                     // CREATED | PAID | FAILED | EXPIRED
  "amount_paise": 649900,
  "amount_refunded_paise": 0,
  "method": "CARD",                     // UPI | CARD | NETBANKING | WALLET | null until paid
  "method_detail": {                    // safe display fields only, by method:
    "card_network": "RuPay", "card_last4": "4242"   // CARD
    // "bank": "HDFC Bank"                          // NETBANKING
    // "wallet": "paytm"                            // WALLET
    // "vpa": "as***@okhdfcbank"                    // UPI (masked)
  },
  "failure_reason": null,
  "paid_at": "2026-09-16T10:05:00Z",
  "created_at": "2026-09-16T10:00:00Z"
}
```

#### Endpoints
| Method & path | Auth | Request body | Success | Errors |
|---|---|---|---|---|
| `POST /api/trekker/payments/orders` | Bearer, TREKKER | `{ booking_id }` | `201` PaymentOrder (reuses the open `CREATED` order for that booking, if any) | `400 VALIDATION_FAILED`, `404 BOOKING_NOT_FOUND`, `409 BOOKING_NOT_PAYABLE`, `502 GATEWAY_UNAVAILABLE` |
| `POST /api/trekker/payments/{id}/verify` | Bearer, TREKKER (owner) | `{ razorpay_order_id, razorpay_payment_id, razorpay_signature }` | `200` Payment (still `CREATED` if Razorpay hasn't captured yet) | `400 PAYMENT_SIGNATURE_INVALID`, `404 PAYMENT_NOT_FOUND`, `502 GATEWAY_UNAVAILABLE` |
| `GET /api/trekker/payments/{id}` | Bearer, TREKKER (owner) | — | `200` Payment | `404 PAYMENT_NOT_FOUND` |
| `POST /api/webhooks/razorpay` | `X-Razorpay-Signature` | Razorpay event (raw) | `200` (including duplicates and ignored events) | `400 WEBHOOK_SIGNATURE_INVALID` |

A payment owned by another user returns `404 PAYMENT_NOT_FOUND`, so the API doesn't reveal that it exists. Verifying a payment that is already `PAID` returns `200` with the Payment.

#### Schema (built — see §6.4)
- **`payments`** columns:
  - `id UUID PK`
  - `user_id UUID FK → users`
  - `booking_id UUID NOT NULL` (becomes an FK once bookings exist)
  - `razorpay_order_id TEXT UNIQUE NOT NULL`
  - `razorpay_payment_id TEXT UNIQUE NULL`
  - `amount_paise BIGINT NOT NULL CHECK > 0`
  - `amount_refunded_paise BIGINT NOT NULL DEFAULT 0 CHECK (0 ≤ amount_refunded_paise ≤ amount_paise)`
  - `currency TEXT NOT NULL DEFAULT 'INR'`
  - `status TEXT NOT NULL CHECK IN ('CREATED','PAID','FAILED','EXPIRED')`
  - `method TEXT NULL CHECK IN ('UPI','CARD','NETBANKING','WALLET')`
  - `method_detail JSONB NULL`
  - `failure_code TEXT NULL`
  - `failure_reason TEXT NULL`
  - `paid_at TIMESTAMPTZ NULL`
  - `created_at`, `updated_at TIMESTAMPTZ NOT NULL`

  Constraints and indexes:
  - Partial unique index on `booking_id WHERE status = 'PAID'`.
  - Index on `(status, created_at)` for the reconciler.
- **`payment_refunds`** — `id UUID PK`, `payment_id UUID FK → payments`, `razorpay_refund_id TEXT UNIQUE NULL` (set once Razorpay accepts), `amount_paise BIGINT NOT NULL CHECK > 0`, `status TEXT NOT NULL CHECK IN ('PENDING','PROCESSED','FAILED')`, `reason TEXT NOT NULL`, `created_at`, `updated_at`.
- **`webhook_events`** — `event_id TEXT PK` (Razorpay `x-razorpay-event-id`), `event_type TEXT NOT NULL`, `received_at TIMESTAMPTZ NOT NULL`, `processed_at TIMESTAMPTZ NULL`.

#### Backend
- **`com.sahyatri.payment.{controller,service,repository,entity,dto}`:**
  - `PaymentController` (trekker routes) and `RazorpayWebhookController`.
  - `PaymentService`: create, verify, apply-captured and fail.
  - `RefundService`.
  - `PaymentReconciler` (`@Scheduled`).
- **`payment/gateway/`:** `PaymentGateway` interface with `createOrder`, `fetchPayment`, `fetchOrderPayments` and `createRefund`.
  - `RazorpayGateway` uses Spring `RestClient` against `https://api.razorpay.com/v1` with basic auth (`key_id:key_secret`) and short timeouts. Errors map to `GATEWAY_UNAVAILABLE`.
  - `FakePaymentGateway` is for tests. This follows the `auth/sms/SmsSender` pattern.
- **Shared changes:**
  - `common/util/HashingUtils` gains `hmacSha256Hex(key, data)` and a constant-time `hexEquals`.
  - `SecurityConfig` adds `permitAll` for `/api/webhooks/**`.
  - The webhook controller takes the body as `byte[]` and verifies the signature before Jackson parses it.
- **Config (`app.payments.*`, `PaymentProperties` record):** `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET` (in `.env.example`). Creating an order returns `GATEWAY_UNAVAILABLE` while the keys are blank. Only `key_id` is ever sent to the browser.
- **Tests (Testcontainers + `FakePaymentGateway`):**
  - Signature: good, bad and tampered.
  - Amount is taken from the server, never the client.
  - Webhook: a bad signature returns `400`; the same event delivered twice is processed once.
  - Verify and webhook racing end in exactly one `PAID`.
  - A `payment.failed` event followed by a retry ends in `PAID`.
  - Reconciler: `CREATED` becomes `EXPIRED`, and a missed capture becomes `PAID`.
  - Refund bookkeeping and the `amount_refunded_paise` cap.
  - Another user's payment returns `404`.

#### Frontend
- `api/payments.ts`: types and calls through `withAuth`.
- `lib/razorpay.ts`: loads `checkout.js` once and wraps `new Razorpay(opts)` in a Promise. `handler` leads to verify. `modal.ondismiss` and the `payment.failed` event lead to polling `GET /api/trekker/payments/{id}`, because a webhook may already have completed the payment.
- The pay button lives on the booking screen. States: paying, paid, failed (retry) and expired (start again).
- `errorMessages.ts` gets copy for the new error codes.

#### Dev setup
- Use Razorpay **test mode** keys (`rzp_test_…`). On the dashboard, turn on UPI, cards, netbanking and wallets, turn off EMI and Pay Later, and turn on auto-capture.
- Local webhooks: expose `:8081` with a tunnel (cloudflared or ngrok), register `<tunnel>/api/webhooks/razorpay` with the five events above, and put its secret in `RAZORPAY_WEBHOOK_SECRET`.
- Pay with the test cards, test UPI IDs and test netbanking bank listed in Razorpay's test-mode docs. No real money moves.

#### Changes when built
- An order can be created only for a `HELD` booking whose hold hasn't expired (else `409 BOOKING_NOT_PAYABLE`). `amount` = `booking.amount_paise`.
- `PaymentOrder` also returns `booking_id`, `checkout_timeout_seconds` (hold time left minus 60 s, at least 60; pass it as Checkout's `timeout`) and `description` (track name and date).
- `PAYMENT_ORDER_TTL` is replaced by the booking hold (`BOOKING_HOLD_TTL`, default 10 min). The reconciler runs every minute:
  - a `HELD` booking past its hold: captured payment on its order → apply it; else its `CREATED` payments → `EXPIRED`, the booking → `EXPIRED`, seats released;
  - a `CREATED` payment older than the hold TTL whose booking is no longer `HELD` → apply a capture if one exists, else `EXPIRED`;
  - a `PENDING` refund that Razorpay hasn't accepted yet → submitted again (after checking the payment's refunds for one carrying our `notes.refund_id`, so it is never refunded twice).
- A capture is applied even to an `EXPIRED` payment (UPI can complete late). So `verify` never returns `PAYMENT_ALREADY_COMPLETED`; that code is dropped. A capture whose booking can't be confirmed any more is refunded in full (`LATE_CAPTURE`, §7.6).
- A captured amount that doesn't match `amount_paise` is logged and not applied.
- `amount_refunded_paise` is reserved when a refund row is created (so the cap holds even while Razorpay is processing). `refund.failed` marks the row `FAILED`, releases the reservation and writes `REFUND_FAILED` to the audit log for an admin to follow up.
- Refunds are sent to Razorpay after the database commit, one transaction per refund.
- Gateway fees are absorbed (not passed on) in V1. International cards stay off.

### 7.5 Catalog: tracks, departures, admin, guides
**Status:** contract agreed; backend (`com.sahyatri.catalog`, `com.sahyatri.admin`, `common/audit`) and frontend implemented.

**Scope:** an admin runs the catalog: tracks (a route) and departures (a dated, priced run of a track with one guide). Anyone can browse published departures. Guide self-service and the protocol acknowledgement required by law 3 come in a later feature; until then the admin publishes, and the check is a single hook in `DepartureAdminService.assertPublishable`.

#### Accounts
- **Admin bootstrap:** at startup, every existing account whose email is in `ADMIN_EMAILS` (comma-separated, case-insensitive) becomes `ADMIN`. Sign up first, then restart; the new role shows after the next sign-in or token refresh.
- **Guides:** an admin promotes an existing `TREKKER` to `GUIDE` by email. Access tokens carry the role, so the guide sees it after their next refresh (≤ 15 min).
- `User.role` can change; the refresh flow always issues the current role.

#### Shapes
```jsonc
// DepartureSummary — public list item
{
  "id": "…-uuid",
  "track": { "slug": "rajmachi-fort", "name": "Rajmachi Fort", "region": "Lonavala", "difficulty": "EASY", "duration_days": 2 },
  "guide": { "id": "…-uuid", "full_name": "Sagar Patil", "avatar_url": null },
  "start_date": "2026-10-24",
  "end_date": "2026-10-25",
  "price_paise": 219900,           // per seat
  "max_group_size": 6,
  "seats_left": 4,
  "bookable": true                 // PUBLISHED, seats_left > 0 and today (IST) ≤ start_date − booking_cutoff_days
}

// DepartureDetail — public detail = DepartureSummary plus
{
  "status": "PUBLISHED",          // PUBLISHED | CANCELLED | EXPIRED | COMPLETED (never DRAFT)
  "track": { /* as above */ "summary": "…", "description": "…", "max_altitude_m": 822, "meeting_point": "Lonavala station, 6:30 am" }
}

// Track — admin
{ "id", "slug", "name", "region", "difficulty", "duration_days", "max_altitude_m", "summary", "description", "meeting_point", "created_at", "updated_at" }

// AdminDeparture — admin list/detail
{
  "id", "track": { "id", "slug", "name", "duration_days" }, "guide": { "id", "full_name", "email", "avatar_url" },
  "start_date", "end_date", "price_paise", "max_group_size", "seats_taken",
  "status": "DRAFT",               // DRAFT | PUBLISHED | CANCELLED | EXPIRED | COMPLETED
  "guide_share_bps": null,         // frozen at publish
  "published_at": null,
  "cancelled_at": null, "cancel_reason_code": null, "cancel_reason_note": null,
  "created_at", "updated_at"
}

// Guide — admin
{ "id", "full_name", "email", "phone", "avatar_url", "created_at" }
```

#### Endpoints
| Method & path | Auth | Request body | Success | Errors |
|---|---|---|---|---|
| `GET /api/public/departures?month=YYYY-MM&difficulty=` | — | — | `200 { items: DepartureSummary[] }`: `PUBLISHED`, `start_date ≥ today`, ordered by `start_date` | `400 VALIDATION_FAILED` |
| `GET /api/public/departures/{id}` | — | — | `200` DepartureDetail (any status except `DRAFT`; `bookable` tells the UI) | `404 DEPARTURE_NOT_FOUND` |
| `GET /api/admin/tracks` | ADMIN | — | `200 { items: Track[] }` by name | — |
| `POST /api/admin/tracks` | ADMIN | Track (editable fields) | `201` Track | `400 VALIDATION_FAILED`, `409 SLUG_TAKEN` |
| `PUT /api/admin/tracks/{id}` | ADMIN | Track (editable fields) | `200` Track | `404 TRACK_NOT_FOUND`, `409 SLUG_TAKEN`, `409 TRACK_IN_USE` (changing `duration_days` once any departure uses the track; departures store their end date) |
| `GET /api/admin/departures` | ADMIN | — | `200 { items: AdminDeparture[] }`, newest `start_date` first | — |
| `POST /api/admin/departures` | ADMIN | `{ track_id, guide_id, start_date, price_paise, max_group_size }` | `201` AdminDeparture (`DRAFT`) | `400 VALIDATION_FAILED`, `404 TRACK_NOT_FOUND`, `409 NOT_A_GUIDE` |
| `PUT /api/admin/departures/{id}` | ADMIN | same as POST | `200` AdminDeparture | `404 DEPARTURE_NOT_FOUND`, `409 DEPARTURE_NOT_DRAFT`, `409 NOT_A_GUIDE` |
| `POST /api/admin/departures/{id}/publish` | ADMIN | — | `200` AdminDeparture (`PUBLISHED`) | `404`, `409 DEPARTURE_NOT_DRAFT`, `409 NOT_A_GUIDE`, `409 START_DATE_TOO_SOON` |
| `POST /api/admin/departures/{id}/cancel` | ADMIN | `{ reason_code, reason_note }` | `200` AdminDeparture (`CANCELLED`) | `400 VALIDATION_FAILED`, `404`, `409 DEPARTURE_NOT_CANCELLABLE` (not `PUBLISHED`, or already started) |
| `DELETE /api/admin/departures/{id}` | ADMIN | — | `204` (drafts only) | `404`, `409 DEPARTURE_NOT_DRAFT` |
| `GET /api/admin/guides` | ADMIN | — | `200 { items: Guide[] }` | — |
| `POST /api/admin/guides` | ADMIN | `{ email }` | `201` Guide | `400 VALIDATION_FAILED`, `404 USER_NOT_FOUND`, `409 ALREADY_GUIDE`, `409 ROLE_NOT_PROMOTABLE` (the account is an admin) |

#### Validation
| Field | Rule |
|---|---|
| `slug` | 3–80 chars, lowercase letters/digits separated by single hyphens |
| `name`, `region` | 1–100 after trim |
| `summary` | 1–200 after trim |
| `description` | 1–5000 after trim |
| `meeting_point` | 1–300 after trim |
| `duration_days` | 1–7 |
| `max_altitude_m` | null or 1–9000 |
| `price_paise` | 10000–10000000 (₹100–₹1,00,000) |
| `max_group_size` | 1–10 (law 2) |
| `start_date` | today (IST) or later on create/edit; at least `booking_cutoff_days` ahead on publish |
| `reason_code` | `WEATHER`, `PERMIT_DENIED`, `GUIDE_UNAVAILABLE`, `SAFETY` |
| `reason_note` | 1–1000 after trim (required) |

#### Behaviour
- Publish freezes `guide_share_bps` from `app.catalog.guide-share-bps` (law 7) and writes `DEPARTURE_PUBLISHED` to `audit_events`. No money moves (law 6).
- Cancel is force majeure only (law 4); it writes `DEPARTURE_CANCELLED` with the reason. From §7.6 on it also cancels and fully refunds every paid booking.
- `DepartureLifecycleJob` (daily at 00:10 IST, and once at startup): `PUBLISHED` with `start_date ≤ today` and `seats_taken = 0` → `EXPIRED` (law 9, not a cancellation); `PUBLISHED` with `end_date < today` → `COMPLETED`. Both are audited with a null actor.
- Dates are judged on the Indian calendar (`Asia/Kolkata`).

#### Backend
- `catalog/`: `PublicCatalogController`, `AdminTrackController`, `AdminDepartureController`; `CatalogService` (public reads), `TrackAdminService`, `DepartureAdminService`, `DepartureLifecycleJob`; entities `Track`, `Departure`, enums `Difficulty`, `DepartureStatus`, `CancelReason`; `DepartureRepository.findByIdForUpdate` (`PESSIMISTIC_WRITE`) for every seat or status change.
- `admin/`: `AdminBootstrap` (`ApplicationRunner`), `AdminGuideController`, `GuideAdminService`.
- `common/audit/AuditLog.record(actorId, action, entityType, entityId, data)`: a JDBC insert (`data` serialised to `jsonb`) that joins the caller's transaction. `common/web/ItemsResponse` wraps list responses.
- `GlobalExceptionHandler`: a malformed path id (e.g. not a UUID) → `404 NOT_FOUND`; a malformed query parameter → `400 VALIDATION_FAILED`.
- Config: `app.catalog.*` (`CatalogProperties`: `guide-share-bps` (`GUIDE_SHARE_BPS`) default 7000 — placeholder until the business sets it, `booking-cutoff-days` default 1, `zone` `Asia/Kolkata`); `app.admin.emails` (`ADMIN_EMAILS`). `@EnableScheduling` is on.
- Tests (Testcontainers): `CatalogTests` (public list/detail/filters, draft hidden, publish rules, frozen share, validation, role guards, cancel reason required, lifecycle job), `AdminGuideTests` (promotion, bootstrap).

#### Frontend
- `api/catalog.ts`, `api/admin.ts`; `lib/format.ts` (rupees, dates).
- `HomePage` departures section reads `GET /api/public/departures` (sample data removed); cards link to `/departures/:id`.
- `/departures/:id` (`pages/DepartureDetailPage.tsx`): track, guide, dates, seats left, price, "Book seats" CTA (booking arrives in §7.6).
- `/admin` (`RequireAuth role="ADMIN"`, `pages/admin/`): Departures (create, edit draft, publish, cancel), Tracks, Guides. Header shows "Admin" for admins.

### 7.6 Bookings, holds and cancellations
**Status:** contract agreed; backend (`com.sahyatri.booking`, `com.sahyatri.payment`) and frontend implemented.

**Scope:** anyone books 1–10 seats on a bookable departure (§7.5) — as a guest with three fields (name, WhatsApp number, email; no password, no OTP) or signed in (e.g. one Google tap). Starting a booking **holds** the seats for 10 minutes while they pay through Razorpay (§7.4). Travellers, medical and dietary details are collected after payment. Payment confirms the booking. A trekker may cancel a confirmed booking before the start date for a refund that depends on how early they cancel. A force-majeure cancellation of the departure (§7.5, law 4) cancels every confirmed booking with a full refund.

#### Rules
- **No booking prerequisites.** Nothing is gated behind a verified phone, an emergency contact or a health declaration.
- **Contact:** every booking stores `contact` = `full_name` (1–100), `phone` (WhatsApp, Indian mobile §7.2), `email`. Guests give all three. A signed-in trekker may leave any out and the account's value is used; still missing → `400 VALIDATION_FAILED` on that field.
- **Guest checkout:** `POST /api/public/bookings` creates a **guest** trekker account (`user.guest: true`, no sign-in identity) and signs it in (access token in the body, refresh cookie as §7.2), so payment and the booking pages work as for any trekker. It never signs into an existing account, even when the email or phone matches one. A failed hold creates nothing. Signed-in trekkers must use `POST /api/trekker/bookings` (the guest call would replace their session). A guest keeps access through the refresh cookie; verifying a phone or email (§7.3) makes the account a normal one.
- **Travellers:** optional at hold time. When given, exactly `seats` of them. `PUT …/travellers` sets them later — on a `HELD` or `CONFIRMED` booking before the start date (else `409 TRAVELLERS_LOCKED`); it replaces the whole list. Each: `full_name` 1–100 after trim, `date_of_birth` (age 18–100 on the start date), `gender`, optional `phone` (Indian mobile, §7.2). `travellers_complete` says whether every seat is named.
- **Seats (law 2):** a hold takes seats immediately under the departure row lock; `seats_taken` never exceeds `max_group_size` (DB CHECK backstop). Lock order everywhere: departure → booking → payment.
- **One live booking** (`HELD` or `CONFIRMED`) per trekker per departure → else `409 ALREADY_BOOKED` (`details.booking_id`).
- **Hold:** `hold_expires_at` = now + `BOOKING_HOLD_TTL` (10 min). The trekker can release it early. An expired hold releases its seats (the reconciler checks Razorpay first, §7.4).
- **Confirmation:** a captured payment turns a `HELD` booking `CONFIRMED` and freezes the refund tiers onto it.
- **Late capture:** a capture that arrives after the hold ended (`EXPIRED`/`RELEASED`) confirms the booking if the departure is still `PUBLISHED`, hasn't started, has enough seats and the trekker has no other live booking on it; otherwise the money is refunded in full (`LATE_CAPTURE`). A capture on a booking that is already confirmed or cancelled is refunded in full too.
- **Law 1:** no job or admin action cancels a confirmed booking except force majeure. Low fill never does.
- **Trekker cancellation:** only a `CONFIRMED` booking, and only before the start date (IST). It releases the seats (they can be booked again) and refunds `floor(amount_paise × refund_bps / 10000)` where `refund_bps` comes from the booking's frozen tiers by `days_before_start` = start date − today (IST):

  | Days before start | Refund |
  |---|---|
  | 15 or more | 90% (`9000`) |
  | 7–14 | 50% (`5000`) |
  | 1–6 | 0% — cancellation still allowed, frees the seats |
  | 0 or after | not allowed |

  Tiers are configuration (`app.bookings.refund-tiers`). The whole booking is cancelled; removing single travellers is out of scope.
- **Force majeure (law 4):** in the same transaction as the departure cancellation, every `CONFIRMED` booking → `CANCELLED_FORCE_MAJEURE` with a refund of everything not yet refunded (`FORCE_MAJEURE`); every `HELD` booking → `RELEASED` (a capture arriving later is refunded as a late capture). `seats_taken` ends at 0.
- **Audit:** `BOOKING_CONFIRMED`, `BOOKING_CANCELLED` (with the refund), `BOOKING_CANCELLED_FORCE_MAJEURE`, `REFUND_FAILED`.

#### Shapes
```jsonc
// Booking — every booking endpoint
{
  "id": "…-uuid",
  "status": "HELD",                 // HELD | CONFIRMED | EXPIRED | RELEASED | CANCELLED_BY_TREKKER | CANCELLED_FORCE_MAJEURE
  "seats": 2,
  "price_paise_per_seat": 219900,
  "amount_paise": 439800,
  "contact": { "full_name": "Asha Rao", "phone": "+919876543210", "email": "asha@example.com" },
  "travellers_complete": false,     // every seat has a named traveller
  "hold_expires_at": "2026-09-16T10:30:00Z",
  "confirmed_at": null,
  "cancelled_at": null,
  "departure": {
    "id": "…-uuid",
    "status": "PUBLISHED",
    "start_date": "2026-10-24",
    "end_date": "2026-10-25",
    "meeting_point": "Lonavala station, 6:30 am",
    "cancel_reason_code": null,     // set when the departure was cancelled
    "cancel_reason_note": null,
    "track": { "slug", "name", "region", "difficulty", "duration_days" },
    "guide": { "id", "full_name", "avatar_url" }
  },
  "travellers": [
    { "full_name": "Asha Rao", "phone": "+919876543210", "date_of_birth": "1995-04-12", "gender": "FEMALE" }
  ],
  "payment": null,                  // latest Payment (§7.4), if any
  "refunds": [
    { "id": "…", "amount_paise": 395820, "status": "PENDING", "kind": "TREKKER_CANCELLATION", "created_at": "…" }
  ],
  "refund_policy": [                // null until confirmed
    { "min_days_before": 15, "refund_bps": 9000 },
    { "min_days_before": 7, "refund_bps": 5000 },
    { "min_days_before": 0, "refund_bps": 0 }
  ],
  "created_at": "…"
}

// CancellationQuote
{ "allowed": true, "days_before_start": 20, "refund_bps": 9000, "refund_paise": 395820 }
```

#### Endpoints
All TREKKER except the guest call (public). Another user's booking returns `404 BOOKING_NOT_FOUND`.

| Method & path | Request body | Success | Errors |
|---|---|---|---|
| `POST /api/public/bookings` (guest) | `{ departure_id, seats, full_name, phone, email }` | `201 { booking: Booking (HELD), auth: AuthResponse (§7.2, is_new_user: true) }` + refresh cookie | `400 VALIDATION_FAILED`, `404 DEPARTURE_NOT_FOUND`, `409 DEPARTURE_NOT_BOOKABLE`, `409 NOT_ENOUGH_SEATS` |
| `POST /api/trekker/bookings` | `{ departure_id, seats, full_name?, phone?, email?, travellers?: [{ full_name, phone?, date_of_birth, gender }] }` | `201` Booking (`HELD`) | `400 VALIDATION_FAILED` (`travellers[1].date_of_birth` style keys), `404 DEPARTURE_NOT_FOUND`, `409 DEPARTURE_NOT_BOOKABLE`, `409 NOT_ENOUGH_SEATS` (`details.seats_left`), `409 ALREADY_BOOKED` |
| `PUT /api/trekker/bookings/{id}/travellers` | `{ travellers: [...] }` (exactly `seats`) | `200` Booking | `400`, `404`, `409 TRAVELLERS_LOCKED` |
| `GET /api/trekker/bookings` | — | `200 { items: Booking[] }`, newest first | — |
| `GET /api/trekker/bookings/{id}` | — | `200` Booking | `404 BOOKING_NOT_FOUND` |
| `DELETE /api/trekker/bookings/{id}/hold` | — | `200` Booking (`RELEASED`) | `404`, `409 BOOKING_NOT_HELD` |
| `GET /api/trekker/bookings/{id}/cancellation-quote` | — | `200` CancellationQuote (`allowed: false` with zeros when not cancellable) | `404` |
| `POST /api/trekker/bookings/{id}/cancel` | — | `200` Booking (`CANCELLED_BY_TREKKER`) | `404`, `409 BOOKING_NOT_CANCELLABLE` |

Payments: §7.4 endpoints, with the changes listed there. Checkout `prefill` comes from the booking's `contact`.

#### Backend
- `booking/`: `BookingController`, `GuestBookingController`; `BookingService` (hold, guest hold, travellers, read, release, quote, cancel, expire hold), `BookingPaymentHandler` (apply a capture to a booking, late-capture rules), `ForceMajeureCanceller` (listens to `DepartureCancelledEvent`, published inside the departure-cancel transaction); entities `Booking`, `BookingTraveller`, `BookingStatus`; `RefundPolicy` (tier lookup).
- `payment/`: `PaymentController`, `RazorpayWebhookController`; `PaymentService` (order, verify, apply capture/failure), `RefundService` (reserve → submit after commit → webhook/reconciler finish), `WebhookService`, `PaymentReconciler`; entities `Payment`, `PaymentRefund`; `gateway/` (`PaymentGateway`, `RazorpayGateway`, `RazorpayJson`).
- `Departure.takeSeats/releaseSeats`. `HashingUtils.hmacSha256Hex(String, byte[])`. `SecurityConfig` permits `/api/webhooks/**`.
- JSONB columns (`refund_policy`, `method_detail`) are `String` fields written with `?::jsonb`.
- Config: `app.bookings.hold-ttl` (`BOOKING_HOLD_TTL`, 10m), `app.bookings.refund-tiers`; `app.payments.*`.
- Tests (Testcontainers + a fake gateway): `BookingTests`, `GuestCheckoutTests`, `SeatConcurrencyTests`, `PaymentTests`, `WebhookTests`, `CancellationTests`.

#### Frontend
- `api/bookings.ts` (`createGuestBooking`, `updateTravellers`), `api/payments.ts`, `lib/razorpay.ts`.
- `/departures/:id`: seat meter, guide, full price, other dates of the same trek (`components/catalog/OtherDepartures.tsx`); CTA → `/book/:id`, no sign-in. No booking screen mentions a minimum group size.
- `/book/:departureId` (`pages/trekker/BookPage.tsx`, **public**): seats (up to `seats_left`, max 10), Google one tap for visitors, then name + WhatsApp (+91) + email (prefilled from the account, or from a returning guest's last booking), itemised price and "Hold & pay" in the right column. Visitors go through `POST /api/public/bookings` and are signed in as a guest; signed-in trekkers use `POST /api/trekker/bookings`. Then the booking page opens Checkout with a 10-minute countdown. `NOT_ENOUGH_SEATS` → "seats just went": nothing charged, details kept, "Take the N seats", and other dates of the trek (same date first) that open their checkout with the details carried over.
- `/account/bookings/:id` (`BookingDetailPage.tsx`): status, contact, payment, refunds, "Pay now" for a live hold, "Release seats", "Cancel booking" with the quote. After payment the travellers form (`PUT …/travellers`) opens until every seat is named; editable until the start date.
- Guests: a "keep access to your trip" notice on the booking and profile pages (verify a mobile or email), and "Sign out" asks for confirmation.
- `/account/bookings` (`BookingsPage.tsx`, "My treks", upcoming and past — see §7.10).
- Header: "My treks" link for trekkers.

### 7.7 Trek and guide pages
**Status:** backend and frontend implemented.

**Scope:** one trek, many guides. The trek page lists every upcoming published departure of a track; each departure has its own guide. Clicking a guide anywhere in the booking flow opens their page.

#### Endpoints (public)
| Method & path | Success | Errors |
|---|---|---|
| `GET /api/public/tracks/{slug}` | `200 { track: TrackDetail, departures: [{ id, start_date, end_date, price_paise, max_group_size, seats_left, bookable, guide: GuideCard }] }`, soonest first | `404 TRACK_NOT_FOUND` |
| `GET /api/public/guides/{id}` | `200 { id, full_name, avatar_url, home_city, bio, treks_led, treks: [{ track: TrackBrief, times }], upcoming: DepartureSummary[] }` | `404 GUIDE_NOT_FOUND` (not a guide, disabled or unknown) |

- `TrackDetail` (also on `GET /api/public/departures/{id}`) adds `distance_km`, `base_altitude_m`, `highest_camp_m`, `stay`, `season_label`, `pickup_drop`, `cloakroom`, `offloading`, `offloading_price_paise`, `itinerary: ItineraryDay[]`, and `photos` (§7.8).
- `ItineraryDay` = `{ day, summary, description, distance_km, start_altitude_m, high_altitude_m, end_altitude_m, hours_min, hours_max, route_note }`; everything after `summary` may be null.
- `GuideCard` = `{ id, full_name, avatar_url, home_city, led_this_trek, years_leading, languages, certification, certification_number, quote, rating, review_count }`; `GET /api/public/departures/{id}` returns it as `guide`. Credentials come from §7.12 (null until filled), `rating` (one decimal, null with no reviews) and `review_count` from §7.14.
- The trek page response also carries `content` (every `ContentKind` → `[{ badge, title, body }]`, shared items first, §7.11), `snow_report` (newest, or null, §7.13), `crowd` (`[{ reported_on, place, tents }]`, last 12 counts, oldest first), `refund_tiers` (`[{ min_days_before, refund_bps }]` from `app.bookings.refund-tiers`, highest first) and `charity` (`{ name, bps }` from `app.charity`, null when no name is set).
- The guide page adds `years_leading`, `languages`, `certification`, `certification_number`, `quote`, `rating`, `review_count` and `reviews` (latest 20, §7.14).
- Counts are `COMPLETED` departures, computed at read time and never stored (law 8). `home_city` and `bio` come from the guide's own profile (§6.2).
- Admin tracks (`POST`/`PUT /api/admin/tracks`) accept and return the route facts, the services (`pickup_drop` ≤ 120, `cloakroom`, `offloading`, `offloading_price_paise` > 0 and only with `offloading: true`) and `itinerary: ItineraryDay[]` without `day` (empty or exactly `duration_days` entries → else `400` on `itinerary`; blank heading → `400` on `itinerary[i].summary`; `hours_max` < `hours_min` → `400` on `itinerary[i].hours_max`). No altitude (track or day) can exceed `max_altitude_m`.
- Altitudes are stored in metres and shown in feet (`lib/format.ts` `feet`, rounded to 5 ft so feet typed in the admin round-trip). Admin forms take feet.

#### Frontend
- `/treks/:slug` (`pages/TrekPage.tsx`) follows the Kedarkantha design: hero photo, name, then on phones the departures before everything else (a sticky right column from `lg`).
  - Departures (`components/catalog/TrekDepartures.tsx`): "N dai (mountain guides) lead …" intro, guide chips, month tabs with date counts, one card per date (dates, price, guide, seat bar where dark = taken, "4 of 10 seats left"). The open card (first by default) introduces the guide: photo, "Name — home", years leading · summits of this trek · languages, certification and number, rating · reviews, quote, "Get to know <name> →" (guide page), a "book these dates now" link and the charity line.
  - Main column: fact cards (duration, maximum altitude, difficulty, pickup and drop, cloakroom, offloading), the snow report panel (§7.13) with the crowd chart, sticky section tabs, Overview (paragraphs split on blank lines), Photos (carousel, §7.8), Day by day (altitude bars — the summit dark, the chosen day laterite — over the day cards), What's included / not included (collapsible), Safety (dark callout, checklist, notes), Cancellation policy (current refund tiers), FAQ and Why choose The Empty Valley. Sections with no content and their tabs are left out. Pieces in `components/catalog/{TrekSections,SnowReportPanel,TrailPhotos,DayByDay}.tsx`.
- `/departures/:id` (`pages/DepartureDetailPage.tsx`): the dates as the headline with the fill bar ("5 of 10 filled · 5 open"), then the guide's profile above the Book button (`components/catalog/GuideProfileCard.tsx`: photo, home, rating, "Certified" when a certificate and number are on file, years leading, summits of this trek, languages, certification, quote, "Know your guide →"), a price card (full price per person, what's extra, charity line, Book; a fixed Book bar on phones), what the price covers and doesn't (open), trek facts, day by day with each day's date, refund tiers with the date each ends, and other dates of the trek. Lists, refund tiers and the charity come from the trek page query (no extra endpoint).
- The trek page's open date card leads to `/departures/:id` ("View these dates →") and to the guide page.
- `/guides/:id` (`pages/GuidePage.tsx`, "Know your guide"): photo, name, home, rating; stat cards (years leading, treks led, rating, languages); quote and bio; a credentials list; reviews; treks led; and a "Walk with <name>" column of upcoming departures with fill bars, Details and Book.
- Guide names link to `/guides/:id` on the trek, departure, checkout, booking and other-dates rows. Seat bars read "6 of 10 seats filled · 4 open".
- Admin track editor has four sections: Details (facts, services, overview and the day-by-day fields), Photos, Page lists (§7.11) and Snow reports (§7.13).
- Tests: `TrekPageTests`, `TrekContentTests`.

### 7.8 Trek photos
**Status:** backend and frontend implemented.

**Scope:** an admin uploads photos from past runs of a trek (views, camps, the group on the trail). The trek page shows them so trekkers can see what they're signing up for. Table in §6.8.

#### Shapes
```jsonc
// TrackPhoto — on TrackDetail.photos (public) and Track.photos (admin), oldest upload first
{ "id": "…-uuid", "url": "http://localhost:8081/api/public/files/track-photos/<id>.jpg",
  "caption": "Summit ridge at first light", "place": "Kedarkantha summit", "day_number": 4 }
```

#### Endpoints
| Method & path | Auth | Request | Success | Errors |
|---|---|---|---|---|
| `POST /api/admin/tracks/{id}/photos` | ADMIN | multipart: `file` (JPEG/PNG ≤ 5 MB), optional `caption` (≤ 200 after trim; blank = none), `place` (≤ 100), `day_number` (a day of the trek) | `201` TrackPhoto | `400 UNSUPPORTED_IMAGE`, `400 VALIDATION_FAILED` (`caption`, `place`, `day_number`), `404 TRACK_NOT_FOUND`, `409 TOO_MANY_PHOTOS` (30), `413 FILE_TOO_LARGE` |
| `PUT /api/admin/tracks/{id}/photos/{photoId}` | ADMIN | `{ caption, place, day_number }` (blank clears) | `200` TrackPhoto | `400 VALIDATION_FAILED`, `404 PHOTO_NOT_FOUND` |
| `DELETE /api/admin/tracks/{id}/photos/{photoId}` | ADMIN | — | `204` | `404 PHOTO_NOT_FOUND` |
| `GET /api/public/files/track-photos/{id}.jpg` | — | — | `200 image/jpeg`, cached for a year (immutable) | `404 NOT_FOUND` |

#### Behaviour
- Uploads are decoded and re-encoded as JPEG (`common/storage/Images`, shared with avatars), scaled so the long edge is ≤ 2000 px, never enlarged. No metadata (EXIF location) survives.
- The file is written before the row and deleted after it, so a listed photo always has a file; a failed write leaves at most an orphan file.
- The admin UI scales photos to ≤ 2000 px in the browser before upload (`lib/photos.ts`), so phone photos over 5 MB still go through and arrive upright (EXIF rotation applied).

#### Frontend
- `/treks/:slug`: the first photo becomes the hero (ridgeline until there is one). "Photos from the trail" (`components/catalog/TrailPhotos.tsx`): one large photo with caption and "place · Day N", arrows, swipe and arrow keys, "1 of 10", thumbnails underneath.
- Admin track editor → Photos: each photo with editable caption, place and day, and Delete; "Add photos" (multi-select) with caption, place and day for the batch.
- Tests: `TrackPhotoTests`.

### 7.8 Launch readiness: notifications, providers, rate limits, legal pages
**Status:** implemented.

**Providers (switched by config, dev defaults unchanged):**
- **Email:** `common/mail/MailTransport` with `LoggingMailTransport` (`MAIL_PROVIDER=log`) and `SesMailTransport` (`ses`). `account/mail/DefaultEmailSender` composes the account emails and hands them to the transport.
- **SMS:** `auth/sms/Msg91SmsSender` (`SMS_PROVIDER=msg91`, MSG91 Flow API, DLT-approved template using `##otp##`). If sending fails, it returns `503 SMS_UNAVAILABLE`.
- **Uploads:** `common/storage/S3FileStorage` (`STORAGE_TYPE=s3`, `S3_UPLOADS_BUCKET`). `/api/public/files/**` still serves them, so `avatar_url` is unchanged.
- **AWS credentials and region** come from the ECS task role and `AWS_REGION` (the SDK default chain).

**Booking emails (`booking/notify/`):**
- `BookingService`, `BookingPaymentHandler` and `ForceMajeureCanceller` publish a `BookingNotice` next to the `BOOKING_CONFIRMED`, `BOOKING_CANCELLED` and `BOOKING_CANCELLED_FORCE_MAJEURE` audit records.
- `BookingNotifier` emails the booking's `contact_email` **after commit**. A mail failure is logged and never undoes a payment or cancellation.
- The emails:
  - **Confirmed:** trek, dates, meeting point, seats, amount paid, and a link to add travellers.
  - **Cancelled by the trekker:** the refund amount, or "no refund due".
  - **Cancelled for force majeure:** the reason note and the full refund.

**Rate limiting (`common/web/RateLimitFilter`, `app.rate-limit.*`):**
- Token buckets per client IP and rule, in memory. That's correct for one backend task; move them to a shared store before scaling out.
- It runs just after Spring Security, so a 429 carries CORS headers.
- The client IP comes from `RATE_LIMIT_CLIENT_IP_HEADER` (`CloudFront-Viewer-Address` in prod), else the socket address. It never uses `X-Forwarded-For`.

| Rule | Limit / min / IP |
|---|---|
| `POST /api/auth/{signup,login,google}`, `/api/auth/otp/**` | 10 |
| `POST /api/public/bookings` | 5 |
| `/api/account/**` writes | 20 |
| every other `/api/**` (incl. refresh, `/me`) | 120 |
| `/api/webhooks/**`, `/api/public/health` | exempt |

- Over the limit → `429 RATE_LIMITED`, `Retry-After` header, `details.retry_after` (seconds).
- The shared API test context sets `app.rate-limit.enabled=false`. `RateLimitFilterTests` covers the filter directly.

**Production packaging:**
- `backend/Dockerfile`: multi-stage, JRE 21, non-root user.
- `server.forward-headers-strategy: framework`, graceful shutdown, actuator liveness/readiness probes, `DB_POOL_SIZE`.
- In production the SPA and API share one origin (`VITE_API_BASE_URL=""`).

**Frontend:**
- `/terms` (`TermsPage`) and `/privacy` (`PrivacyPage`, DPDP Act 2023) are new, linked from the footer.
- `/contact` now shows the support email, phone, hours and address, and `/vision` has the About content.
- "Lead a trek" is a footer link only. It opens the external guide sign-up form (Tally, `SITE_LINKS.leadATrek`) in a new tab. The `/lead-a-trek` placeholder page is gone.
- Header, once signed in: the avatar opens a profile menu. Trekkers see "My profile" and "Sign out"; other roles see only "Sign out". The other account pages are reached from the account sidebar. Admins keep the "Admin" link beside it. There's no separate "My treks" or "Sign out" in the bar.
- Business details live in `lib/business.ts`. **It holds placeholders that must be filled in before launch.**
- New error copy for `RATE_LIMITED` and `SMS_UNAVAILABLE`.

**CI/CD:**
- `.github/workflows/ci.yml` runs the backend tests and the frontend lint and build.
- `deploy.yml` deploys `main` after CI passes, through GitHub OIDC:
  - backend: image → ECR → ECS rolling deploy;
  - frontend: build → S3 → CloudFront invalidation.

**Tests:** `RateLimitFilterTests`, `BookingNotificationTests`.

### 7.9 Trek catalog (`/treks`)
**Status:** backend and frontend implemented.

**Scope:** one page listing every trek we run and the dates open on each, browsable by trek or by date. Until this, "All Treks" only jumped to the landing rail, which shows a trek solely while it has a published departure, so a trek with no dates yet was invisible. Column in §6.9.

#### Shapes
```jsonc
// CatalogTrek — `departures` empty means "dates coming soon"
{
  "slug": "rajmachi-fort", "name": "Rajmachi Fort", "region": "Lonavala", "difficulty": "EASY",
  "duration_days": 2, "summary": "Twin forts above Lonavala", "max_altitude_m": 822,
  "season_label": "Monsoon · Jun–Sep",
  "cover_url": "http://localhost:8081/api/public/files/track-photos/<id>.jpg",  // first photo, else null
  "departures": [
    { "id": "…-uuid", "start_date": "2026-10-10", "end_date": "2026-10-11", "price_paise": 249900,
      "max_group_size": 10, "seats_left": 7, "bookable": true,
      "guide": { "id": "…-uuid", "full_name": "Sagar Pawar", "avatar_url": null } }
  ]
}
```

#### Endpoints
| Method & path | Auth | Request | Success | Errors |
|---|---|---|---|---|
| `GET /api/public/tracks` | — | — | `200 { items: CatalogTrek[] }` | — |
| `PUT /api/admin/tracks/{id}/listed` | ADMIN | `{ "listed": true }` | `200` TrackResponse | `400 VALIDATION_FAILED` (`listed`), `404 TRACK_NOT_FOUND` |

#### Behaviour
- A track is in the catalog when it has an upcoming `PUBLISHED` departure **or** `listed` is true. Tracks with dates come first (soonest departure first), then listed tracks with no dates, by name.
- Each track carries **every** upcoming published departure, soonest first, so the UI can filter by month and group by date without another call. Drafts, past dates and other statuses never appear.
- `cover_url` is the track's first uploaded photo (§7.8), or null.
- Toggling `listed` writes `TRACK_LISTED` / `TRACK_UNLISTED` to `audit_events`; setting it to what it already is writes nothing. Unlisting never hides a track that has upcoming dates — those are public through their departures either way.

#### Frontend
- `/treks` (`pages/TreksPage.tsx`), public: filters for grade, length (one day / weekend / 3 days +) and month, each kept in the query string so a filtered catalog can be linked. Two views, also in the query string (`?view=dates`):
  - **By trek** — cards with the cover photo, region, grade, summary, length, altitude, "from ₹X" and the next three dates as chips with seats left; "+N more" and the card itself open the trek page. No dates → "Dates coming soon" with the season label.
  - **By date** — the same departures grouped by month: dates, trek, seat meter, guide and Book.
- Header "All Treks" now points at `/treks`; the landing rail keeps its teaser and gains "See all treks →".
- Admin tracks list: an "In catalog" checkbox per track (`setTrackListed`), for treks with no dates yet.
- Tests: `TrekCatalogTests`.

### 7.10 Trekker dashboard (`/account`)
**Status:** frontend implemented on existing endpoints; the pieces marked *placeholder* have no backend yet.

**Scope:** the trekker account area follows the "Trekker Dashboard Design": a sidebar with My treks, My profile and Gear (Overview was dropped; `/account` redirects to My treks). No new endpoints or schema. Everything real comes from `GET /api/trekker/bookings` (§7.6) and `User` (§7.2).

#### Screens
- ~~`/account` (`pages/trekker/OverviewPage.tsx`)~~ *removed from the app*: "Namaste, <first name>", member since (`user.created_at`), treks completed (`CONFIRMED` bookings whose end date has passed). The next upcoming booking as a slate card: days to departure, trek, date and length, guide, seats (you + companions), meeting point, "View booking" (or "Complete payment" for a hold) and "Arrange gear". "Before you leave": payment and traveller details (real), waiver and gear rental (placeholder). Itinerary and guide contact: placeholder. No upcoming booking → "Find a departure".
- `/account/bookings` (`BookingsPage.tsx`, "My treks"): chips pick an upcoming booking; its card shows status, departure, guide, meeting point, seats, traveller-details state, "Manage booking" / "Rent gear", and a "⋯" menu holding "Request cancellation" (links to the booking page with `?cancel=1`). Cancelling is deliberately out of the way: on the booking page the "Change of plans?" section is hidden until opened from its own "⋯" menu (or that link), leads with "Keep my booking", and cancelling takes "Continue to cancel" → the refund quote → "Yes, cancel my booking" (§7.6). "Trip arrangements" follows the design's four columns (add-ons with "Request", Veg / Veg + egg, medical certificate upload, dark coordinator card) with every control disabled and a "Coming soon" tag; no coordinator or waiver data is shown until those exist. Past list: "Completed <month> · with <guide>" or the booking status.
- `/account/gear` (`GearPage.tsx`, `?booking=` picks the trek): no visible title or intro (a screen-reader-only heading); trek chips, a fixed catalogue of six items and a "Your rental" summary, all disabled.
- Shared: `lib/trips.ts` (upcoming/past split, days until), `components/trips/TripPieces.tsx` (`ComingSoon`, `Fact`, `PaperCard`, `TrekPicker`).

#### Not built (placeholders in the design)
Gear rental and its payment, add-ons (offload, transport), per-trek food option and its lock, medical-document upload, waivers, trip coordinator, itinerary PDF, guide contact release. Each needs its own schema and endpoints when it's picked up. The design's "balance due" is not used: bookings are paid in full (§7.6).

### 7.11 Trek-page lists (shared and per trek)
**Status:** backend and frontend implemented. Table in §6.10.

**Scope:** the trek page's lists — what's included and not, safety (checklist, the dark "who decides to turn back" callout, notes), FAQ and "Why choose us" cards. Some are the same on every trek (shared), some are the trek's own; the page shows shared items first, then the trek's.

#### Endpoints (ADMIN)
| Method & path | Request | Success | Errors |
|---|---|---|---|
| `GET /api/admin/content` | — | `200` `{ INCLUDED: ContentItem[], …every kind }` (shared lists) | — |
| `PUT /api/admin/content/{kind}` | `{ items: [{ badge, title, body }] }` (≤ 40, in order; empty clears) | `200` shared lists | `400 VALIDATION_FAILED`, `404` unknown kind |
| `GET /api/admin/tracks/{id}/content` | — | `200` the track's own lists | `404 TRACK_NOT_FOUND` |
| `PUT /api/admin/tracks/{id}/content/{kind}` | as above | `200` the track's own lists | as above |

- `body` 1..2000 (trimmed), `title` ≤ 200, `badge` ≤ 12. `FAQ`, `WHY_US` and `SAFETY_CALLOUT` need a `title` (`items[i].title`); only `WHY_US` takes a `badge` (`items[i].badge`). A PUT replaces that one list whole.
- Public: `content` on `GET /api/public/tracks/{slug}` (§7.7). Only the first `SAFETY_CALLOUT` is shown.

#### Frontend
- `components/admin/ContentEditor.tsx`: one panel per list with add, remove, reorder, undo and its own Save. `/admin/content` ("Page content" tab) edits the shared lists; Tracks → Edit → Page lists edits a trek's own.
- Tests: `TrekContentTests`.

### 7.12 Guide credentials
**Status:** backend and frontend implemented. Table `guide_profiles` in §6.10.

**Scope:** what trekkers read about a guide before choosing a date: years leading (from the year they started), languages, certification and its number, and a line in their own words. An admin fills them in.

| Method & path | Request | Success | Errors |
|---|---|---|---|
| `GET /api/admin/guides/{id}/details` | — | `200 { guide_id, leading_since, years_leading, languages, certification, certification_number, quote }` (all null until saved) | `404 GUIDE_NOT_FOUND` |
| `PUT /api/admin/guides/{id}/details` | `{ leading_since (1950..this year), languages ≤ 120, certification ≤ 160, certification_number ≤ 60, quote ≤ 240 }`, blank clears | `200` as above | `400 VALIDATION_FAILED`, `404 GUIDE_NOT_FOUND` |

- Shown on `GuideCard` (§7.7) and the guide page. Frontend: Admin → Guides → "Credentials" on each guide. Tests: `TrekContentTests`.

### 7.13 Snow reports and crowd counts
**Status:** backend and frontend implemented. Table `snow_reports` in §6.10.

**Scope:** every Tuesday an admin, or a guide who leads the trek, files what the trail is like: snowline, night temperature at base camp, labelled readings ("Juda ka Talab": "Frozen", "Road, Purola to Sankri": "Open"), the tents counted at the busiest camp, a note and a photo taken that morning. Every report is kept; the newest shows on the trek page, and the last 12 tent counts show as a chart so trekkers can pick a quiet week. Reports are never edited or deleted; a correction is a newer report.

| Method & path | Auth | Request | Success | Errors |
|---|---|---|---|---|
| `GET /api/guide/tracks` | GUIDE | — | `200 { items: [{ id, slug, name }] }` — tracks with a `PUBLISHED` or `COMPLETED` departure they lead | — |
| `GET /api/{admin,guide}/tracks/{id}/snow-reports` | ADMIN / GUIDE | — | `200 { items: SnowReport[] }`, newest first (≤ 52) | `403 FORBIDDEN` (guide not leading it), `404 TRACK_NOT_FOUND` |
| `POST /api/{admin,guide}/tracks/{id}/snow-reports` | ADMIN / GUIDE | `{ reported_on, reported_from, snowline_m?, night_temp_c?, conditions?: [{label ≤ 40, value ≤ 60}] (≤ 4), crowd_place?, crowd_tents? (0..2000), note? ≤ 500 }` | `201` SnowReport | `400 VALIDATION_FAILED` (`reported_on` in the future; `crowd_place`/`crowd_tents` given alone), `403`, `404` |
| `POST /api/{admin,guide}/snow-reports/{id}/photo` | ADMIN / GUIDE | multipart `file` | `200` SnowReport with `photo_url` | `403`, `404 REPORT_NOT_FOUND`, `409 REPORT_HAS_PHOTO` |
| `GET /api/public/files/snow-reports/{id}.jpg` | — | — | `200 image/jpeg` | `404` |

- `SnowReport` = `{ id, reported_on, reported_from, snowline_m, night_temp_c, conditions, crowd_place, crowd_tents, note, photo_url, reported_by: { id, full_name }, created_at }`.
- Frontend: `components/admin/SnowReports.tsx` (form prefilled with last week's labels and places, plus the history) under Tracks → Edit → Snow reports, and for guides at `/guide` (`pages/guide/GuideReportsPage.tsx`, "Snow reports" in the header). Public panel: `components/catalog/SnowReportPanel.tsx`.
- Tests: `SnowReportTests`.

### 7.14 Guide reviews
**Status:** backend and frontend implemented. Table `reviews` in §6.10.

**Scope:** after a departure is `COMPLETED`, each trekker with a `CONFIRMED` booking on it can rate the guide 1–5 with optional words, and change it later. Ratings are averaged at read time (never stored, like every guide figure — law 8). Reviews show with the author's first name.

| Method & path | Auth | Request | Success | Errors |
|---|---|---|---|---|
| `GET /api/trekker/bookings/{id}/review` | TREKKER (owner) | — | `200 { id, booking_id, rating, body, created_at, updated_at }` | `404 BOOKING_NOT_FOUND`, `404 REVIEW_NOT_FOUND` |
| `PUT /api/trekker/bookings/{id}/review` | TREKKER (owner) | `{ rating: 1..5, body?: ≤ 2000 }` | `200` as above (creates or rewrites) | `400`, `404 BOOKING_NOT_FOUND`, `409 REVIEW_NOT_ALLOWED` (not confirmed, or the departure isn't completed) |
| `GET /api/public/guides/{id}/reviews` | — | — | `200 { items: [{ rating, body, author_name, trek_name, trek_start_date, created_at }] }`, newest first (≤ 20) | — |

- Frontend: "How was it with <guide>?" on a completed booking (`components/booking/ReviewSection.tsx`); reviews on the guide page; rating on the trek page's guide intro. No moderation yet.
- The local dev mock (`src/devMock.ts`, gitignored) seeds sample reviews, the Kedarkantha page and a snow report history.
- Tests: `ReviewTests`.

### 7.15 Acquisition tracking and admin Insights
**Status:** backend and frontend implemented. Columns in §6.11. Plan and rationale: the "User data & acquisition analytics plan" doc.

**Scope:** record where every account and booking came from, how people heard of us, and marketing consent, and show the funnel, sources and business health to admins. Everything on the dashboard is computed at read time; nothing aggregated is stored.

**Capture (frontend, `src/analytics/attribution.ts`):** `captureVisit()` runs once per page load (before the router) and reads `utm_source|medium|campaign|term|content`, `gclid`, `fbclid`, the outside referrer and the landing path. The first visit is kept in `localStorage` (`tev.first_touch`) and never replaced; `tev.last_touch` is replaced by any visit with tags or an outside referrer. Storage failures are swallowed. Sign-up, OTP verify, Google, guest checkout and signed-in booking requests all send:

```
acquisition?: {
  first_touch?: Touch, last_touch?: Touch,        // Touch = { utm_*?, gclid?, fbclid?, referrer?, landing_path?, seen_at? }
  device_type?: "MOBILE" | "TABLET" | "DESKTOP",  // anything else is ignored
  heard_from?: HeardFrom, heard_from_note?: ≤ 200,
  marketing_email?: boolean, marketing_whatsapp?: boolean
}
```

- A **new** account (any of the four ways in) keeps the first touch (else the last), device, `signup_method`, heard-from (the note only with a `heard_from`) and consent; consent given here is audited with `via: SIGNUP`. An existing account signing in ignores all of it.
- A booking keeps the last touch (else the first) and the device.
- Only `heard_from` (unknown value) and `heard_from_note` (> 200) can fail validation → `400 VALIDATION_FAILED`.
- `UserResponse` gains `marketing_email`, `marketing_whatsapp` (booleans).

| Method & path | Auth | Request | Success | Errors |
|---|---|---|---|---|
| `PATCH /api/account/marketing-consent` | any signed-in | `{ email?: boolean, whatsapp?: boolean }` (left out = unchanged) | `200 UserResponse` | `401` |
| `GET /api/admin/insights?days=30` | ADMIN | `days` 1..365, default 30 | `200 InsightsResponse` (below) | `400 VALIDATION_FAILED`, `403` |

Every actual consent change writes `MARKETING_CONSENT_GRANTED` / `MARKETING_CONSENT_WITHDRAWN` (`entity_type USER`, `data { channel: EMAIL|WHATSAPP, via: SIGNUP|ACCOUNT }`).

**`InsightsResponse`** — `days`, `from`, `to`, and ("accounts" = trekker accounts; days are IST):
- `headline` — `new_accounts`, `bookings_held` (created in window), `bookings_confirmed`, `gross_paise` (amount of bookings confirmed in window, before refunds), `hold_to_paid_bps` (of holds made in window and no longer `HELD`), `avg_group_size`, `cancellations`.
- `daily[]` — `{ date, accounts, confirmed }` for every day of the window.
- `funnel[]` — one cohort, trekkers who signed up in the window: `ACCOUNT` → `HELD` → `PAID` → `TREKKED` (confirmed on a `COMPLETED` departure) → `REVIEWED`, distinct people.
- `sources[]`, `campaigns[]` — `{ source, accounts, confirmed_bookings, gross_paise }`, top 20 by gross. Accounts by first touch; bookings and gross by each booking's last touch. Source = UTM source, else `google-ads`/`meta-ads` from a click id, else the referrer's host, else `direct`; rows with nothing captured are `unknown`.
- `heard_from[]`, `signup_methods[]`, `devices[]` — `{ key, count }` over new accounts (`NO_ANSWER` / `UNKNOWN` for missing).
- `payments` — orders created in window: `attempts`, `paid`, `failed`, `success_bps` (paid ÷ not `CREATED`), `methods[]`, `failures[]` (top 5 reasons).
- `treks[]` — per trek, bookings confirmed in window: `confirmed_bookings`, `seats`, `gross_paise`, with all-time `avg_rating`, `reviews`. Top 10.
- `upcoming[]` — published departures starting in the next 60 days with `seats_taken` / `max_group_size`.
- `guides[]` — all-time, computed at read time (law 8): `departures_completed`, `avg_fill_bps` (over completed), `avg_rating`, `reviews`.
- `marketing_reach` — active trekker accounts, and how many opted in by email / WhatsApp.

**Privacy:** safety data (emergency contact, blood group, medical notes, allergies, height, weight) never feeds analytics or marketing. No IP addresses or precise location are stored. The dashboard returns aggregates only.

- Frontend: "How did you hear about us?" + two unticked consent boxes on sign-up (all three methods) and guest checkout (`components/auth/SignupChoicesFields.tsx`); "Communication preferences" on the profile (`components/profile/CommunicationSection.tsx`); admin **Insights** tab (`/admin/insights`, `pages/admin/InsightsPage.tsx`) with a 7/30/90-day switch; the Privacy page lists what we collect.
- Tests: `AcquisitionTests`, `InsightsTests`.

### Charity share
`app.charity` (`CHARITY_NAME`, `CHARITY_BPS`, default 100 = 1%) is part of the price, never added on top. It only shows as a line on the trek page ("1% goes to …, and the rest runs the company"); no money is split or recorded per booking yet.

## 8. Running locally

```bash
docker compose up -d                     # from trek/ — Postgres on host port 5433
cd backend && ./mvnw spring-boot:run     # http://localhost:8081
cd frontend && npm run dev               # http://localhost:5173
cd backend && ./mvnw test                # needs Docker running
```

First admin: sign up, put that email in `ADMIN_EMAILS`, restart the backend, sign in again (§7.5).
Payments: set Razorpay test-mode `RAZORPAY_KEY_ID` / `RAZORPAY_KEY_SECRET`; for webhooks also a tunnel and `RAZORPAY_WEBHOOK_SECRET` (§7.4 Dev setup). Without keys, everything up to "Hold seats & pay" works and paying returns `GATEWAY_UNAVAILABLE`.

Guides: `docs/BACKEND.md`, `docs/FRONTEND.md`, `docs/API.md` (every endpoint with curl). Production: see `docs/DEPLOY.md`. The same image runs locally with `docker build -t sahyatri-backend backend`.

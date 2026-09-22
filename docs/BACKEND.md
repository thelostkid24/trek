# Backend guide

The backend is one Spring Boot service (`backend/`) with a PostgreSQL database. It serves the JSON API in `docs/API.md` and runs two background jobs. `docs/TRD.md` is the source of truth for contracts and rules; this guide is the map for working in the code.

## Stack
| Piece | Choice |
|---|---|
| Language / framework | Java 21, Spring Boot 4.1 (Web MVC, Data JPA, Validation, Security, OAuth2 resource server for JWTs) |
| Database | PostgreSQL 16; schema only through Flyway migrations (`ddl-auto: validate`) |
| Build | Maven wrapper (`./mvnw`) |
| Tests | JUnit 5, MockMvc, Testcontainers (real Postgres, never H2) |
| Integrations | Razorpay (payments), Google Identity (sign-in), AWS S3 (uploads), Amazon SES (email), MSG91 (SMS) |

## Run it
```bash
docker compose up -d                              # Postgres on localhost:5433
cd backend && ./mvnw spring-boot:run              # http://localhost:8081
cd backend && ./mvnw test                         # needs Docker running
docker build -t sahyatri-backend backend          # production image
```
Config comes from environment variables, with the repo-root `.env` read automatically in dev. Every variable is listed in `.env.example`. With no keys set, email and SMS are printed to the log, uploads go to `./data/uploads`, and paying returns `GATEWAY_UNAVAILABLE`.

## Code layout
Feature first, then layer: `com.sahyatri.<feature>.{controller,service,repository,entity,dto}`. Code shared across features lives in `common`.

| Package | What's in it |
|---|---|
| `auth` | Sign-up, login, phone OTP, Google, JWT access tokens, rotating refresh tokens. `auth/sms` holds the SMS senders (log, MSG91) |
| `account` | Things any signed-in user changes about their account: photo, email (with a verification link), phone, password. `account/mail` composes account emails |
| `profile` | The trekker profile (personal, emergency, medical and gear details) |
| `catalog` | Treks (`tracks`), their photos and itineraries, departures, public trek/guide/departure pages, admin CRUD, and `DepartureLifecycleJob` |
| `admin` | Admin bootstrap from `ADMIN_EMAILS`, promoting trekkers to guides |
| `booking` | Seat holds, guest checkout, travellers, cancellations and refund tiers, force-majeure handling. `booking/notify` emails the contact after confirm/cancel |
| `payment` | Razorpay orders, signature checks, webhooks, refunds, and `PaymentReconciler`. `payment/gateway` wraps the Razorpay REST API |
| `common/config` | `@ConfigurationProperties` records (one per area) and CORS |
| `common/security` | The filter chain, JWT beans, JSON 401/403, the refresh cookie |
| `common/exception` | `ApiException`, `ApiError` and `GlobalExceptionHandler` |
| `common/storage` | `FileStorage` with local-disk and S3 implementations, public image serving |
| `common/mail` | `MailTransport` with log and SES implementations |
| `common/web` | `ItemsResponse` and `RateLimitFilter` |
| `common/audit` | `AuditLog`, the append-only `audit_events` table |
| `common/util` | Hashing and HMAC helpers |

## How a request flows
1. `RateLimitFilter` rejects the request with 429 if the client's IP is over its limit.
2. Spring Security checks the JWT and role (`SecurityConfig`); public, auth and webhook routes skip it.
3. A thin controller validates the body (Bean Validation on DTO records) and calls a service.
4. The service does the work: ownership checks from the JWT subject, row locks, state changes, audit records, domain events.
5. Errors are thrown as `ApiException` and rendered as `{ code, message, details }`.

## Rules the code must keep
These come from the product laws in TRD §5. Where possible they're enforced by a database constraint as well as the service.
- A paid booking is never cancelled for low fill; only a force-majeure departure cancellation ends it, with a full refund.
- `max_group_size` ≤ 10, and seats taken can never exceed it (DB CHECK plus a departure row lock).
- Only published departures can be booked.
- Guide share is frozen on the departure when it's published.
- Guide ranking and "treks led" counts are computed when read, never stored.
- Money is integer paise (`long`, `_paise`); percentages are basis points (`_bps`).

## Data model
One Flyway file per feature in `src/main/resources/db/migration`. Never edit one that has been applied; add a new file.

| Migration | Adds |
|---|---|
| `V1__auth.sql` | `users`, `refresh_tokens`, `otp_challenges` |
| `V2__trekker_profile.sql` | `trekker_profiles`, `email_verifications`, avatar key |
| `V3__catalog.sql` | `tracks`, `departures`, `audit_events` |
| `V4__bookings_payments.sql` | `bookings`, `booking_travellers`, `payments`, `payment_refunds`, `webhook_events` |
| `V5__guest_checkout.sql` | guest accounts, groups of 10, booking contact fields |
| `V6__trek_page.sql` | route facts, `track_itinerary_days` |
| `V7__trekker_profile_details.sql` | height, weight, diet, allergies, altitude, shoe size |
| `V8__track_photos.sql` | trek photos |

Full column lists are in TRD §6.

## Money flow
1. `POST /api/trekker/bookings` holds seats for 10 minutes (`HELD`).
2. `POST /api/trekker/payments/orders` creates a Razorpay order; the amount always comes from the booking.
3. The browser pays in Razorpay Checkout, then calls `verify`, which checks the HMAC signature.
4. Razorpay's webhook arrives separately (HMAC over the raw body, each event processed once). Verify and webhook go through the same locked method, so exactly one of them confirms.
5. Confirmation freezes the refund tiers onto the booking and emails the contact after commit.
6. Refunds are reserved in the same transaction, sent to Razorpay after commit, and finished by the `refund.*` webhooks.

Lock order everywhere: departure → booking → payment.

## Background jobs
Spring `@Scheduled` only; each takes row locks and uses one transaction per row.

| Job | When | What |
|---|---|---|
| `PaymentReconciler` | every minute | Expires holds past their 10 minutes (after checking Razorpay for a late capture), closes stale orders, retries refunds Razorpay hasn't accepted |
| `DepartureLifecycleJob` | daily 00:10 IST, and once at startup | A published departure that reaches its start date with no seats sold → `EXPIRED` (not a cancellation); one past its end date → `COMPLETED` |

Both run in every backend copy. Keep production at one copy until ShedLock is added.

## Configuration switches
| Variable | Values | Effect |
|---|---|---|
| `STORAGE_TYPE` | `local` / `s3` | Where uploads go (`UPLOAD_DIR` or `S3_UPLOADS_BUCKET`) |
| `MAIL_PROVIDER` | `log` / `ses` | Email printed or sent through SES from `MAIL_FROM` |
| `SMS_PROVIDER` | `log` / `msg91` | OTP printed or sent through MSG91 |
| `RATE_LIMIT_ENABLED` | `true` / `false` | The per-IP limiter (limits in `application.yml` under `app.rate-limit`) |
| `RATE_LIMIT_CLIENT_IP_HEADER` | blank / `CloudFront-Viewer-Address` | Where the real client IP comes from |
| `AUTH_COOKIE_SECURE` | `false` dev / `true` prod | `Secure` flag on the refresh cookie |
| `BOOKING_HOLD_TTL` | e.g. `10m` | Seat hold length |
| `GUIDE_SHARE_BPS` | e.g. `7000` | Guide's share, frozen at publish |

## Tests
Every API test extends `auth/AuthTestSupport`, which shares one Spring context and one Postgres container. It mocks SMS, email, Google and the mail transport, and uses `FakePaymentGateway` instead of Razorpay. Rate limiting is off there; `RateLimitFilterTests` covers it on its own.

| Test class | Covers |
|---|---|
| `AuthControllerTests`, `OtpFlowTests`, `GoogleAuthTests` | Sign-in, tokens, refresh reuse detection |
| `AvatarTests`, `EmailChangeTests`, `PhoneChangeTests`, `PasswordChangeTests` | Account changes |
| `CatalogTests`, `TrekPageTests`, `TrackPhotoTests`, `AdminGuideTests` | Treks, departures, guides, admin |
| `BookingTests`, `GuestCheckoutTests`, `SeatConcurrencyTests`, `CancellationTests` | Holds, seats under concurrency, cancellations, force majeure |
| `PaymentTests`, `WebhookTests` | Signatures, races, reconciler, refunds |
| `BookingNotificationTests` | Booking emails after commit |

## Adding a feature
1. Read the relevant TRD section; add the new schema, endpoints and screens to it in the same change.
2. Add a migration `V<n>__<feature>.sql`.
3. Create the feature package with entity, repository, DTO records (one per file), service, and controller.
4. Put ownership checks in the service, taking identity from the JWT subject.
5. Throw `ApiException` with an `UPPER_SNAKE` code; write an audit record for publish, cancel, refund or payout changes.
6. Add a test class that extends `AuthTestSupport`, then run `./mvnw test`.
7. Add the endpoints to `docs/API.md`.

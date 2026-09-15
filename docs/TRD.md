# Sahyātri — Technical Requirement Document (living)

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
| Payments (planned) | Razorpay (test mode in dev) |
| Background work | Spring `@Scheduled` only — no job-queue service in V1 |

## 2. Repo layout

```
trek/
├── docker-compose.yml        # postgres
├── .env.example              # every env var the system reads
├── docs/TRD.md               # this file
├── backend/
│   └── src/main/java/com/sahyatri/
│       ├── common/           # error handling, security/CORS config, health
│       └── <feature>/        # one package per feature
│   └── src/main/resources/db/migration/   # V<n>__<feature>.sql
└── frontend/
    └── src/
        ├── api/              # fetch client + per-feature API functions
        ├── components/       # shared UI (Layout, …)
        └── pages/            # route components, grouped per role as they appear
```

## 3. Conventions

### 3.1 Backend
- **Package-by-feature**: `com.sahyatri.<feature>` holds its `*Controller`, `*Service`, `*Repository`, entities and DTOs (Java records).
- **Route prefixes by audience**:
  - `/api/public/**` — no auth
  - `/api/auth/**` — login/signup flows
  - `/api/trekker/**`, `/api/guide/**`, `/api/admin/**` — role-guarded
  - `/api/webhooks/**` — signature-verified, no JWT
- **JSON is snake_case** (global Jackson naming strategy). Java stays camelCase.
- **Errors**: always `{ "code": "UPPER_SNAKE", "message": "...", "details": {} }`. Services throw `ApiException`; `GlobalExceptionHandler` renders it. Validation failures → `400 VALIDATION_FAILED` with `details.fields`.
- **Money** is integer **paise** (`long` / `BIGINT`), fields suffixed `_paise`. Percentages are basis points (`_bps`).
- **Time**: `TIMESTAMPTZ` / `Instant` for moments, `DATE` / `LocalDate` for trek dates.
- **IDs**: UUID.
- **Ownership checks live in the service layer**, never only in the controller or UI.
- **Migrations**: one Flyway file per feature (`V2__auth.sql`, …). Never edit a migration that has been applied; add a new one.
- **State changes** that matter for audit (publish, cancel, refunds, payouts) write an audit record (table defined when first needed).

### 3.2 Frontend
- Mobile-first: design at ~375px, enhance with `sm:`/`md:` breakpoints.
- All server calls go through `src/api/client.ts` (`apiFetch`), which throws `ApiError` carrying the backend's `code`.
- Server state via TanStack Query; no global client store unless a feature needs one.
- Brand tokens in `src/index.css` `@theme`.

## 4. Security scope (V1)
1. **Role guards + ownership checks** — every non-public route requires a JWT with the right role; services verify the caller owns the resource.
2. **Webhook signature verification** — Razorpay webhooks verified via HMAC before any processing; processing is idempotent.
3. **Secrets only via environment variables** — listed in `.env.example`, never committed.

> Current state: `SecurityConfig` is **permit-all** until the auth feature lands.

## 5. Product laws the code must enforce
Carried from PRD/TRD v1.2. These are rules, not schema. Each feature that touches them must encode them (DB constraint where possible, else service layer + test).

1. **A paid booking is unconditional.** No code path cancels a paid booking for low fill.
2. **Small batch**: a departure's `max_group_size` ≤ 6; seats sold never exceed it.
3. **Only published departures are bookable.** Publishing happens only after the guide has acknowledged the current track protocol version.
4. **Force majeure is the only cancellation path** (weather, permit denied, guide incapacity with no substitute, safety), requires a stored reason, and triggers a full refund of every paid booking.
5. **Inspection happens at T-2/T-3, never T-0.** A failed item leads to remediation, then a substitute guide. It never leads to cancellation.
6. **No money moves at publish.** Guide draws are capped at collected guide-share minus prior draws. Settlement only after completion with no open remediation or force-majeure flag.
7. **Guide share is configuration**, frozen on the departure at publish.
8. **Guide ranking is computed at read time**, never stored.
9. **A published departure with zero bookings by its start date expires/archives.** It is not a cancellation.

## 6. Schema
Defined per feature. Each feature appends a subsection: tables/columns added, constraints, migration filename.

_(none yet)_

## 7. Feature log
Each feature appends: scope, endpoints, tables, screens, tests.

### 7.0 Foundation
- `GET /api/public/health` → `{ status, database, server_time }`
- Global error shape, CORS for `http://localhost:5173`, permit-all security (temporary).
- Frontend: layout shell, home page showing backend health, 404 page.
- Tests: `HealthControllerTests` (health + 404 error shape against Testcontainers Postgres).

## 8. Running locally

```bash
docker compose up -d                     # from trek/ — Postgres on host port 5433
cd backend && ./mvnw spring-boot:run     # http://localhost:8081
cd frontend && npm run dev               # http://localhost:5173
cd backend && ./mvnw test                # needs Docker running
```

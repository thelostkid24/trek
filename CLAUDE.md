# Sahyātri (trek) — working rules

`docs/TRD.md` is the source of truth. Read the relevant section before touching a feature, and update it in the same change when you add schema, endpoints or screens (§6 schema, §7 feature log).

## Workflow
- **Run the apps only when the user asks.** Otherwise, make the changes and say what to run. When asked, use the `frontend` and `backend` configs in `.claude/launch.json`.
- Explore with the code-review-graph MCP tools first (`semantic_search_nodes`, `query_graph`, `get_impact_radius`), then Grep/Read. The graph only indexes **git-tracked** files — untracked code won't show up; fall back to Grep for it.
- Don't design ahead of the feature that needs it. Keep diffs surgical.

## Backend (Java 21, Spring Boot, Postgres 16 via Flyway)
- Package layout: `com.sahyatri.<feature>.{controller,service,repository,entity,dto}`. Cross-feature code goes in `common.{config,security,exception,util,storage,audit,web}`, never inside a feature.
- Controllers are thin. Services own logic **and ownership checks** (identity comes from the JWT subject, never the request body).
- DTOs are Java records, one per file. JSON is snake_case; Java stays camelCase.
- Errors: throw `ApiException` → `{ "code": "UPPER_SNAKE", "message", "details" }`. Validation → `400 VALIDATION_FAILED` with `details.fields`.
- Money is integer paise (`long`/`BIGINT`, `_paise` suffix). Percentages are basis points (`_bps`). Moments are `Instant`/`TIMESTAMPTZ`; trek dates are `LocalDate`/`DATE`. IDs are UUIDs.
- Schema only through Flyway `V<n>__<feature>.sql` (`ddl-auto: validate`). **Never edit an applied migration**; add a new one.
- Routes: `/api/public/**`, `/api/auth/**`, `/api/account/**`, `/api/{trekker,guide,admin}/**` (role-guarded), `/api/webhooks/**` (signature-verified, no JWT).
- Audit-worthy state changes (publish, cancel, refunds, payouts) write to `AuditLog`.
- Background work is `@Scheduled` only: row locks, one transaction per row.
- Config goes through `@ConfigurationProperties` records in `common/config`; every env var is listed in `.env.example`. Never commit secrets.
- Tests: JUnit 5 + MockMvc + Testcontainers (real Postgres, never H2). `cd backend && ./mvnw test` needs Docker.

## Product laws (TRD §5) — encode as DB constraint where possible, else service + test
1. A paid booking is unconditional; nothing cancels it for low fill.
2. `max_group_size` ≤ 10; seats sold never exceed it.
3. Only published departures are bookable; publishing requires the guide's ack of the current protocol version.
4. Force majeure (stored reason) is the only way we cancel a departure, and it fully refunds every paid booking.
5. Inspection happens at T-2/T-3, never T-0; failure → remediation → substitute guide, never cancellation.
6. No money moves at publish. Guide draws ≤ collected guide share minus prior draws. Settlement happens only after completion with nothing open.
7. Guide share is config, frozen on the departure at publish.
8. Guide ranking is computed at read time, never stored.
9. A published departure with zero bookings by its start date expires; that isn't a cancellation.

Payments: verify the Razorpay webhook HMAC before processing, keep processing idempotent, and re-verify browser-reported success server-side. Card data never touches our servers.

## Frontend (React 19, TS, Vite, Tailwind v4, React Router, TanStack Query)
- Mobile-first (~375px), enhance with `sm:`/`md:`.
- All server calls go through `src/api/client.ts` `apiFetch` (throws `ApiError` carrying the backend `code`).
- Server state lives in TanStack Query; no global store unless a feature needs one.
- Brand tokens live in `src/index.css` `@theme`.
- Checks: `npm --prefix frontend run build` (tsc + vite) and `npm --prefix frontend run lint` (oxlint).

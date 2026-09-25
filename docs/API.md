# Sahyātri API reference

Every HTTP endpoint the backend serves, with a curl example, the request body and the response. The contract details (rules, edge cases, why) live in `docs/TRD.md` §7; this file is the quick lookup.

## Conventions

- **Base URL:** `http://localhost:8081` locally, `https://<domain>` in production (same origin as the site).
- **JSON is snake_case.** Money is integer **paise** (`219900` = ₹2,199). Moments are ISO-8601 UTC (`2026-09-16T10:30:00Z`); trek dates are `YYYY-MM-DD`. IDs are UUIDs.
- **Auth:** sign-in endpoints return an `access_token` (JWT, 15 min) and set an httpOnly refresh cookie `sahyatri_refresh` (30 days, path `/api/auth`). Send the token as `Authorization: Bearer <token>`. When it expires you get `401 TOKEN_EXPIRED`; call `POST /api/auth/refresh`.
- **Roles:** `/api/trekker/**` needs `TREKKER`, `/api/admin/**` needs `ADMIN`, `/api/account/**` any signed-in user, `/api/public/**` nobody.
- **Errors** always look like this:
  ```json
  { "code": "BOOKING_NOT_FOUND", "message": "Booking not found", "details": {} }
  ```
  Validation errors are `400 VALIDATION_FAILED` with `details.fields` (`{"email": "must be a well-formed email address"}`). Common to every endpoint: `401 UNAUTHENTICATED`, `401 TOKEN_EXPIRED`, `403 FORBIDDEN`, `429 RATE_LIMITED` (`details.retry_after` seconds, `Retry-After` header).
- **Rate limits** per IP per minute: 10 on signup/login/Google/OTP, 5 on guest bookings, 20 on account changes, 120 on everything else. Webhooks and health are exempt.

Set these once in your shell for the examples:

```bash
API=http://localhost:8081
TOKEN=<access_token from a sign-in response>
ADMIN=<access_token of an admin>
```

`-c jar.txt` / `-b jar.txt` save and send the refresh cookie.

## Index

| Method | Path | Auth | What it does |
|---|---|---|---|
| GET | `/api/public/health` | — | Server and database health |
| POST | `/api/auth/signup` | — | Create an account with email + password |
| POST | `/api/auth/login` | — | Sign in with email + password |
| POST | `/api/auth/otp/request` | — | Text a 6-digit sign-in code |
| POST | `/api/auth/otp/verify` | — | Sign in (or sign up) with the code |
| POST | `/api/auth/google` | — | Sign in with a Google credential |
| POST | `/api/auth/refresh` | cookie | New access token, rotated cookie |
| POST | `/api/auth/logout` | cookie | Revoke the refresh token |
| GET | `/api/auth/me` | Bearer | The signed-in user |
| POST | `/api/auth/email/verify` | — | Confirm an email from its link |
| GET | `/api/trekker/profile` | TREKKER | Read the trekker profile |
| PUT | `/api/trekker/profile` | TREKKER | Save the trekker profile |
| PUT | `/api/account/avatar` | Bearer | Upload a profile photo |
| DELETE | `/api/account/avatar` | Bearer | Remove the profile photo |
| POST | `/api/account/email` | Bearer | Add or change email (sends a link) |
| POST | `/api/account/phone/otp` | Bearer | Text a code to a new phone |
| POST | `/api/account/phone/verify` | Bearer | Confirm the phone with the code |
| PUT | `/api/account/password` | Bearer | Set or change the password |
| PATCH | `/api/account/marketing-consent` | Bearer | Opt in or out of trek offers |
| GET | `/api/public/departures` | — | Upcoming published departures |
| GET | `/api/public/departures/{id}` | — | One departure with trek and guide |
| GET | `/api/public/tracks/{slug}` | — | Trek page: trek + its departures |
| GET | `/api/public/guides/{id}` | — | Guide page |
| GET | `/api/public/files/avatars/{id}.jpg` | — | Profile photo image |
| GET | `/api/public/files/track-photos/{id}.jpg` | — | Trek photo image |
| POST | `/api/public/bookings` | — | Guest checkout: hold seats + guest sign-in |
| POST | `/api/trekker/bookings` | TREKKER | Hold seats |
| GET | `/api/trekker/bookings` | TREKKER | My bookings |
| GET | `/api/trekker/bookings/{id}` | TREKKER | One booking |
| PUT | `/api/trekker/bookings/{id}/travellers` | TREKKER | Set traveller details |
| DELETE | `/api/trekker/bookings/{id}/hold` | TREKKER | Release a hold early |
| GET | `/api/trekker/bookings/{id}/cancellation-quote` | TREKKER | What a cancel would refund |
| POST | `/api/trekker/bookings/{id}/cancel` | TREKKER | Cancel a confirmed booking |
| POST | `/api/trekker/payments/orders` | TREKKER | Create a Razorpay order for a hold |
| POST | `/api/trekker/payments/{id}/verify` | TREKKER | Verify Checkout's success |
| GET | `/api/trekker/payments/{id}` | TREKKER | Payment status |
| POST | `/api/webhooks/razorpay` | signature | Razorpay events |
| GET | `/api/admin/tracks` | ADMIN | All treks |
| POST | `/api/admin/tracks` | ADMIN | Create a trek |
| PUT | `/api/admin/tracks/{id}` | ADMIN | Edit a trek |
| POST | `/api/admin/tracks/{id}/photos` | ADMIN | Upload a trek photo |
| DELETE | `/api/admin/tracks/{id}/photos/{photoId}` | ADMIN | Delete a trek photo |
| GET | `/api/admin/departures` | ADMIN | All departures |
| POST | `/api/admin/departures` | ADMIN | Create a draft departure |
| PUT | `/api/admin/departures/{id}` | ADMIN | Edit a draft |
| DELETE | `/api/admin/departures/{id}` | ADMIN | Delete a draft |
| POST | `/api/admin/departures/{id}/publish` | ADMIN | Publish (makes it bookable) |
| POST | `/api/admin/departures/{id}/cancel` | ADMIN | Force-majeure cancel + full refunds |
| GET | `/api/admin/guides` | ADMIN | All guides |
| POST | `/api/admin/guides` | ADMIN | Promote a trekker to guide |
| GET | `/api/admin/insights` | ADMIN | Funnel, sources and business numbers |

---

## Shared shapes

### User
```json
{
  "id": "5b0c…-uuid",
  "full_name": "Asha Rao",
  "email": "asha@example.com",
  "phone": "+919876543210",
  "avatar_url": "http://localhost:8081/api/public/files/avatars/9f1e….jpg",
  "role": "TREKKER",
  "email_verified": false,
  "phone_verified": true,
  "guest": false,
  "auth_methods": ["PASSWORD", "PHONE"],
  "marketing_email": true,
  "marketing_whatsapp": false,
  "created_at": "2026-09-01T08:00:00Z"
}
```
`role`: `TREKKER | GUIDE | ADMIN`. `auth_methods`: `PASSWORD | PHONE | GOOGLE`. `guest` is true for accounts created by guest checkout. `marketing_email` / `marketing_whatsapp`: agreed to trek offers on that channel.

### AuthResponse
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiJ9…",
  "token_type": "Bearer",
  "expires_in": 900,
  "is_new_user": false,
  "user": { "…": "User" }
}
```
Every sign-in also sends `Set-Cookie: sahyatri_refresh=…; Path=/api/auth; HttpOnly; SameSite=Lax`.

### Acquisition
Optional `acquisition` object on signup, OTP verify, Google sign-in and both booking endpoints: where the visitor came from, and what they chose at sign-up (TRD §7.15).
```json
{
  "first_touch": { "utm_source": "instagram", "utm_medium": "paid", "utm_campaign": "kedarkantha_dec",
                   "utm_term": null, "utm_content": "reel-1", "gclid": null, "fbclid": "fb.123",
                   "referrer": "https://l.instagram.com/", "landing_path": "/treks/kedarkantha",
                   "seen_at": "2026-09-20T05:30:00Z" },
  "last_touch": { "…": "same shape, the latest tagged or referred visit" },
  "device_type": "MOBILE",
  "heard_from": "FRIEND_FAMILY",
  "heard_from_note": null,
  "marketing_email": true,
  "marketing_whatsapp": false
}
```
- A **new** account keeps the first touch (else the last), `device_type`, `heard_from` (+ `heard_from_note`, ≤ 200) and consent. An existing account signing in ignores all of it.
- A booking keeps the last touch (else the first) and `device_type`.
- Touch values are cleaned, never rejected: trimmed, truncated, tags lower-cased; a non-`http(s)` referrer, a path not starting with `/` or an unknown `device_type` is dropped.
- `heard_from`: `INSTAGRAM | YOUTUBE | GOOGLE_SEARCH | FRIEND_FAMILY | WHATSAPP_GROUP | BLOG_FORUM | OTHER`. An unknown value or a note over 200 chars → `400 VALIDATION_FAILED`.

### Booking
```json
{
  "id": "…-uuid",
  "status": "CONFIRMED",
  "seats": 2,
  "price_paise_per_seat": 219900,
  "amount_paise": 439800,
  "contact": { "full_name": "Asha Rao", "phone": "+919876543210", "email": "asha@example.com" },
  "travellers_complete": false,
  "hold_expires_at": "2026-09-16T10:30:00Z",
  "confirmed_at": "2026-09-16T10:24:11Z",
  "cancelled_at": null,
  "departure": {
    "id": "…-uuid",
    "status": "PUBLISHED",
    "start_date": "2026-10-24",
    "end_date": "2026-10-25",
    "meeting_point": "Lonavala station, 6:30 am",
    "cancel_reason_code": null,
    "cancel_reason_note": null,
    "track": { "slug": "rajmachi", "name": "Rajmachi", "region": "Lonavala", "difficulty": "EASY", "duration_days": 2 },
    "guide": { "id": "…-uuid", "full_name": "Vikram Shinde", "avatar_url": null }
  },
  "travellers": [
    { "full_name": "Asha Rao", "phone": "+919876543210", "date_of_birth": "1995-04-12", "gender": "FEMALE" }
  ],
  "payment": { "…": "Payment" },
  "refunds": [],
  "refund_policy": [
    { "min_days_before": 15, "refund_bps": 9000 },
    { "min_days_before": 7, "refund_bps": 5000 },
    { "min_days_before": 0, "refund_bps": 0 }
  ],
  "created_at": "2026-09-16T10:20:00Z"
}
```
`status`: `HELD | CONFIRMED | EXPIRED | RELEASED | CANCELLED_BY_TREKKER | CANCELLED_FORCE_MAJEURE`. `refund_policy` is null until confirmed. A refund: `{ "id", "amount_paise", "status": "PENDING|PROCESSED|FAILED", "kind": "TREKKER_CANCELLATION|FORCE_MAJEURE|LATE_CAPTURE", "created_at" }`.

### Payment
```json
{
  "id": "…-uuid",
  "booking_id": "…-uuid",
  "status": "PAID",
  "amount_paise": 439800,
  "amount_refunded_paise": 0,
  "method": "upi",
  "method_detail": { "vpa": "asha@okaxis" },
  "failure_reason": null,
  "paid_at": "2026-09-16T10:24:10Z",
  "created_at": "2026-09-16T10:21:00Z"
}
```
`status`: `CREATED | PAID | FAILED | EXPIRED`.

### DepartureSummary
```json
{
  "id": "…-uuid",
  "track": { "slug": "rajmachi", "name": "Rajmachi", "region": "Lonavala", "difficulty": "EASY", "duration_days": 2 },
  "guide": { "id": "…-uuid", "full_name": "Vikram Shinde", "avatar_url": null },
  "start_date": "2026-10-24",
  "end_date": "2026-10-25",
  "price_paise": 219900,
  "max_group_size": 10,
  "seats_left": 4,
  "bookable": true
}
```

### TrackDetail
```json
{
  "slug": "rajmachi", "name": "Rajmachi", "region": "Lonavala", "difficulty": "EASY", "duration_days": 2,
  "summary": "Twin forts above the Ulhas valley", "description": "…",
  "max_altitude_m": 830, "meeting_point": "Lonavala station, 6:30 am",
  "distance_km": 15.5, "base_altitude_m": 620, "highest_camp_m": 800,
  "stay": "Tents · twin share", "season_label": "Monsoon trek · Jun–Sep",
  "itinerary": [ { "day": 1, "summary": "Lonavala to Udhewadi" }, { "day": 2, "summary": "Forts, then back" } ],
  "photos": [ { "id": "…-uuid", "url": "http://localhost:8081/api/public/files/track-photos/….jpg", "caption": "Shrivardhan fort" } ]
}
```
`difficulty`: `EASY | MODERATE | CHALLENGING`.

### GuideCard
`{ "id", "full_name", "avatar_url", "home_city", "led_this_trek": 3 }`

---

## Health

### GET `/api/public/health`
Server and database health. Used by the load balancer and uptime checks.
```bash
curl $API/api/public/health
```
`200`
```json
{ "status": "ok", "database": "up", "server_time": "2026-09-19T09:25:44Z" }
```

---

## Auth

### POST `/api/auth/signup`
Creates a `TREKKER` account with email and password, and signs it in.

| Field | Rule |
|---|---|
| `full_name` | 1–100 chars |
| `email` | valid email, ≤ 254 (lowercased) |
| `password` | 8–72 chars, at least one letter and one digit |

```bash
curl -c jar.txt -X POST $API/api/auth/signup -H 'Content-Type: application/json' \
  -d '{"full_name":"Asha Rao","email":"asha@example.com","password":"trekking1"}'
```
`201` AuthResponse (`is_new_user: true`, `email_verified: false`) + refresh cookie. The email is verified later through `POST /api/account/email`. Optional `acquisition` ([Acquisition](#acquisition)).
Errors: `400 VALIDATION_FAILED`, `409 EMAIL_ALREADY_REGISTERED`.

### POST `/api/auth/login`
Signs in with email and password. Five wrong passwords lock the email for 15 minutes.
```bash
curl -c jar.txt -X POST $API/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"asha@example.com","password":"trekking1"}'
```
`200` AuthResponse + refresh cookie.
Errors: `400 VALIDATION_FAILED`, `401 INVALID_CREDENTIALS`, `403 ACCOUNT_DISABLED`, `429 TOO_MANY_ATTEMPTS`.

### POST `/api/auth/otp/request`
Texts a 6-digit code to an Indian mobile (`+91` then 10 digits starting 6–9). Resend after 30 s; the code lasts 5 min.
```bash
curl -X POST $API/api/auth/otp/request -H 'Content-Type: application/json' -d '{"phone":"+919876543210"}'
```
`202`
```json
{ "expires_in": 300, "resend_after": 30 }
```
Errors: `400 VALIDATION_FAILED`, `429 OTP_RATE_LIMITED` (`details.retry_after`), `503 SMS_UNAVAILABLE`. In dev the code is printed in the backend log.

### POST `/api/auth/otp/verify`
Signs in with the code. An unknown number gets a new account (`full_name` optional). Optional `acquisition` ([Acquisition](#acquisition)).
```bash
curl -c jar.txt -X POST $API/api/auth/otp/verify -H 'Content-Type: application/json' \
  -d '{"phone":"+919876543210","code":"123456","full_name":"Asha Rao"}'
```
`200` AuthResponse + refresh cookie.
Errors: `400 VALIDATION_FAILED`, `400 OTP_INVALID` (`details.attempts_left`), `410 OTP_EXPIRED`, `429 OTP_TOO_MANY_ATTEMPTS`, `403 ACCOUNT_DISABLED`.

### POST `/api/auth/google`
Signs in with the credential from Google Identity Services (the button on the login page). Creates the account on first use. Optional `acquisition` ([Acquisition](#acquisition)).
```bash
curl -c jar.txt -X POST $API/api/auth/google -H 'Content-Type: application/json' \
  -d '{"id_token":"<Google ID token>"}'
```
`200` AuthResponse + refresh cookie.
Errors: `400 VALIDATION_FAILED`, `401 GOOGLE_TOKEN_INVALID`, `403 ACCOUNT_DISABLED`.

### POST `/api/auth/refresh`
Trades the refresh cookie for a new access token and rotates the cookie. Reusing an old cookie revokes every session of that user.
```bash
curl -b jar.txt -c jar.txt -X POST $API/api/auth/refresh
```
`200` AuthResponse + new cookie.
Errors: `401 REFRESH_TOKEN_INVALID`.

### POST `/api/auth/logout`
Revokes the refresh token and clears the cookie. Safe to call without one.
```bash
curl -b jar.txt -X POST $API/api/auth/logout
```
`204` No body.

### GET `/api/auth/me`
Returns the signed-in user.
```bash
curl $API/api/auth/me -H "Authorization: Bearer $TOKEN"
```
`200` User.
Errors: `401 UNAUTHENTICATED`, `401 TOKEN_EXPIRED`.

### POST `/api/auth/email/verify`
Confirms an email address with the token from the emailed link (`/account/verify-email?token=…`). No sign-in needed.
```bash
curl -X POST $API/api/auth/email/verify -H 'Content-Type: application/json' -d '{"token":"<token from the link>"}'
```
`200`
```json
{ "email": "asha@example.com" }
```
Errors: `400 VALIDATION_FAILED`, `400 EMAIL_TOKEN_INVALID`, `410 EMAIL_TOKEN_EXPIRED`, `409 EMAIL_ALREADY_REGISTERED`.

---

## Profile and account

### GET `/api/trekker/profile`
Returns the trekker profile. Fields are null until first saved.
```bash
curl $API/api/trekker/profile -H "Authorization: Bearer $TOKEN"
```
`200`
```json
{
  "full_name": "Asha Rao",
  "avatar_url": null,
  "date_of_birth": "1995-04-12",
  "gender": "FEMALE",
  "home_city": "Pune",
  "experience_level": "INTERMEDIATE",
  "highest_altitude_m": 4200,
  "bio": "Weekend trekker.",
  "emergency_contact": { "name": "Ravi Rao", "relation": "Brother", "phone": "+919812345678" },
  "height_cm": 162,
  "weight_kg": 55,
  "blood_group": "B+",
  "allergies": null,
  "medical_notes": null,
  "diet": "VEGETARIAN",
  "shoe_size_uk": 5,
  "completion": { "percent": 80, "missing": ["medical_notes"] },
  "updated_at": "2026-09-10T12:00:00Z"
}
```
Errors: `403 FORBIDDEN` (not a trekker).

### PUT `/api/trekker/profile`
Saves the whole profile (send every field; omitted optional fields become null).

| Field | Rule |
|---|---|
| `full_name` | required, ≤ 100 |
| `gender` | `FEMALE`, `MALE`, `NON_BINARY`, `PREFER_NOT_TO_SAY` |
| `experience_level` | `BEGINNER`, `INTERMEDIATE`, `EXPERIENCED` |
| `diet` | `VEGETARIAN`, `EGGETARIAN`, `NON_VEGETARIAN`, `VEGAN`, `JAIN` |
| `blood_group` | `A+ A- B+ B- AB+ AB- O+ O-` |
| `emergency_contact` | null, or all of `name` (≤ 100), `relation` (≤ 50), `phone` (Indian mobile) |
| numbers | `height_cm` 100–250, `weight_kg` 25–250, `highest_altitude_m` 0–8849, `shoe_size_uk` 1–15 |
| text | `home_city` ≤ 100, `bio` ≤ 500, `allergies` ≤ 300, `medical_notes` ≤ 1000 |

```bash
curl -X PUT $API/api/trekker/profile -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"full_name":"Asha Rao","date_of_birth":"1995-04-12","gender":"FEMALE","home_city":"Pune",
       "experience_level":"INTERMEDIATE","emergency_contact":{"name":"Ravi Rao","relation":"Brother","phone":"+919812345678"},
       "blood_group":"B+","diet":"VEGETARIAN"}'
```
`200` the saved profile (as GET).
Errors: `400 VALIDATION_FAILED` (nested keys like `emergency_contact.phone`).

### PUT `/api/account/avatar`
Uploads a profile photo (JPEG or PNG, ≤ 5 MB). It is re-encoded to a JPEG.
```bash
curl -X PUT $API/api/account/avatar -H "Authorization: Bearer $TOKEN" -F file=@me.jpg
```
`200` User (new `avatar_url`).
Errors: `400 UNSUPPORTED_IMAGE`, `413 FILE_TOO_LARGE`.

### DELETE `/api/account/avatar`
Removes the profile photo.
```bash
curl -X DELETE $API/api/account/avatar -H "Authorization: Bearer $TOKEN"
```
`200` User (`avatar_url: null`).

### POST `/api/account/email`
Adds or changes the account email. Emails a verification link to the new address; the change happens when it's clicked.
```bash
curl -X POST $API/api/account/email -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"email":"asha.new@example.com"}'
```
`202`
```json
{ "expires_in": 86400 }
```
Errors: `400 VALIDATION_FAILED`, `409 EMAIL_ALREADY_REGISTERED`, `409 EMAIL_ALREADY_VERIFIED`, `429 EMAIL_RATE_LIMITED`.

### POST `/api/account/phone/otp`
Texts a code to a phone the user wants to add or change to.
```bash
curl -X POST $API/api/account/phone/otp -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"phone":"+919876543210"}'
```
`202` `{ "expires_in": 300, "resend_after": 30 }`
Errors: `400 VALIDATION_FAILED`, `409 PHONE_ALREADY_REGISTERED`, `409 PHONE_ALREADY_VERIFIED`, `429 OTP_RATE_LIMITED`, `503 SMS_UNAVAILABLE`.

### POST `/api/account/phone/verify`
Confirms the phone with the code; it becomes the account's verified phone.
```bash
curl -X POST $API/api/account/phone/verify -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"phone":"+919876543210","code":"123456"}'
```
`200` User (`phone_verified: true`).
Errors: `400 VALIDATION_FAILED`, `400 OTP_INVALID`, `410 OTP_EXPIRED`, `429 OTP_TOO_MANY_ATTEMPTS`, `409 PHONE_ALREADY_REGISTERED`.

### PUT `/api/account/password`
Sets a password (accounts without one) or changes it (`current_password` required then). Signs out other sessions and returns a fresh session.
```bash
curl -c jar.txt -X PUT $API/api/account/password -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"current_password":"trekking1","new_password":"summit2026"}'
```
`200` AuthResponse + rotated refresh cookie.
Errors: `400 VALIDATION_FAILED`, `400 CURRENT_PASSWORD_INCORRECT`, `400 EMAIL_REQUIRED`, `429 TOO_MANY_ATTEMPTS`.

### PATCH `/api/account/marketing-consent`
Opts in or out of trek offers by email and WhatsApp. A field left out keeps that channel as it is. Every actual change is audited (`MARKETING_CONSENT_GRANTED` / `MARKETING_CONSENT_WITHDRAWN`).
```bash
curl -X PATCH $API/api/account/marketing-consent -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"email":true,"whatsapp":false}'
```
`200` User (`marketing_email`, `marketing_whatsapp` updated).

---

## Catalog (public)

### GET `/api/public/departures`
Upcoming published departures, soonest first. Optional filters: `month=YYYY-MM`, `difficulty=EASY|MODERATE|CHALLENGING`.
```bash
curl "$API/api/public/departures?month=2026-10&difficulty=EASY"
```
`200`
```json
{ "items": [ { "…": "DepartureSummary" } ] }
```
Errors: `400 VALIDATION_FAILED` (bad month or difficulty).

### GET `/api/public/departures/{id}`
One departure with its trek details and guide. Any status except `DRAFT`; `bookable` says whether it can be booked now.
```bash
curl $API/api/public/departures/<departure-id>
```
`200`
```json
{
  "id": "…-uuid",
  "track": { "…": "TrackDetail" },
  "guide": { "…": "GuideCard" },
  "start_date": "2026-10-24",
  "end_date": "2026-10-25",
  "price_paise": 219900,
  "max_group_size": 10,
  "seats_left": 4,
  "bookable": true,
  "status": "PUBLISHED"
}
```
Errors: `404 DEPARTURE_NOT_FOUND`.

### GET `/api/public/tracks/{slug}`
The trek page: trek details plus every upcoming published departure, soonest first.
```bash
curl $API/api/public/tracks/rajmachi
```
`200`
```json
{
  "track": { "…": "TrackDetail" },
  "departures": [
    { "id": "…-uuid", "start_date": "2026-10-24", "end_date": "2026-10-25", "price_paise": 219900,
      "max_group_size": 10, "seats_left": 4, "bookable": true, "guide": { "…": "GuideCard" } }
  ]
}
```
Errors: `404 TRACK_NOT_FOUND`.

### GET `/api/public/guides/{id}`
A guide's public page. Counts are completed departures.
```bash
curl $API/api/public/guides/<guide-id>
```
`200`
```json
{
  "id": "…-uuid",
  "full_name": "Vikram Shinde",
  "avatar_url": null,
  "home_city": "Lonavala",
  "bio": "Twelve monsoons on these forts.",
  "treks_led": 14,
  "treks": [ { "track": { "slug": "rajmachi", "name": "Rajmachi", "region": "Lonavala", "difficulty": "EASY", "duration_days": 2 }, "times": 9 } ],
  "upcoming": [ { "…": "DepartureSummary" } ]
}
```
Errors: `404 GUIDE_NOT_FOUND`.

### GET `/api/public/files/avatars/{id}.jpg` and `/api/public/files/track-photos/{id}.jpg`
Image files. Use the `avatar_url` / `photos[].url` the API returns rather than building these.
```bash
curl -o photo.jpg $API/api/public/files/track-photos/<photo-id>.jpg
```
`200 image/jpeg`, cached for a year. Errors: `404 NOT_FOUND`.

---

## Bookings

A booking holds seats for 10 minutes while the trekker pays. Payment confirms it and emails the contact.

### POST `/api/public/bookings`
Guest checkout. Holds seats and signs the visitor in as a new guest account (no password or OTP). Signed-in trekkers use `POST /api/trekker/bookings` instead. Optional `acquisition` ([Acquisition](#acquisition)).
```bash
curl -c jar.txt -X POST $API/api/public/bookings -H 'Content-Type: application/json' \
  -d '{"departure_id":"<departure-id>","seats":2,"full_name":"Asha Rao","phone":"+919876543210","email":"asha@example.com"}'
```
`201` + refresh cookie
```json
{ "booking": { "…": "Booking, status HELD" }, "auth": { "…": "AuthResponse, is_new_user true, user.guest true" } }
```
Errors: `400 VALIDATION_FAILED`, `404 DEPARTURE_NOT_FOUND`, `409 DEPARTURE_NOT_BOOKABLE`, `409 NOT_ENOUGH_SEATS` (`details.seats_left`).

### POST `/api/trekker/bookings`
Holds 1–10 seats for a signed-in trekker. Contact fields left out come from the account. Travellers are optional now; if given, exactly `seats` of them (each 18–100 years old on the start date). Optional `acquisition` ([Acquisition](#acquisition)).
```bash
curl -X POST $API/api/trekker/bookings -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"departure_id":"<departure-id>","seats":2,
       "travellers":[{"full_name":"Asha Rao","date_of_birth":"1995-04-12","gender":"FEMALE"},
                     {"full_name":"Ravi Rao","phone":"+919812345678","date_of_birth":"1992-01-03","gender":"MALE"}]}'
```
`201` Booking (`HELD`).
Errors: `400 VALIDATION_FAILED` (keys like `travellers[1].date_of_birth`), `404 DEPARTURE_NOT_FOUND`, `409 DEPARTURE_NOT_BOOKABLE`, `409 NOT_ENOUGH_SEATS`, `409 ALREADY_BOOKED` (`details.booking_id`).

### GET `/api/trekker/bookings`
The trekker's bookings, newest first.
```bash
curl $API/api/trekker/bookings -H "Authorization: Bearer $TOKEN"
```
`200` `{ "items": [ Booking, … ] }`

### GET `/api/trekker/bookings/{id}`
One booking. Another user's booking returns 404.
```bash
curl $API/api/trekker/bookings/<booking-id> -H "Authorization: Bearer $TOKEN"
```
`200` Booking. Errors: `404 BOOKING_NOT_FOUND`.

### PUT `/api/trekker/bookings/{id}/travellers`
Replaces the traveller list (exactly `seats` entries). Allowed on `HELD` or `CONFIRMED` bookings before the start date.
```bash
curl -X PUT $API/api/trekker/bookings/<booking-id>/travellers -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"travellers":[{"full_name":"Asha Rao","date_of_birth":"1995-04-12","gender":"FEMALE"},
                    {"full_name":"Ravi Rao","date_of_birth":"1992-01-03","gender":"MALE"}]}'
```
`200` Booking (`travellers_complete: true`). Errors: `400 VALIDATION_FAILED`, `404 BOOKING_NOT_FOUND`, `409 TRAVELLERS_LOCKED`.

### DELETE `/api/trekker/bookings/{id}/hold`
Releases a hold before paying; the seats go back on sale.
```bash
curl -X DELETE $API/api/trekker/bookings/<booking-id>/hold -H "Authorization: Bearer $TOKEN"
```
`200` Booking (`RELEASED`). Errors: `404 BOOKING_NOT_FOUND`, `409 BOOKING_NOT_HELD`.

### GET `/api/trekker/bookings/{id}/cancellation-quote`
What cancelling now would refund (90% at 15+ days, 50% at 7–14, 0% at 1–6, not allowed on or after the start).
```bash
curl $API/api/trekker/bookings/<booking-id>/cancellation-quote -H "Authorization: Bearer $TOKEN"
```
`200`
```json
{ "allowed": true, "days_before_start": 20, "refund_bps": 9000, "refund_paise": 395820 }
```
Errors: `404 BOOKING_NOT_FOUND`.

### POST `/api/trekker/bookings/{id}/cancel`
Cancels a confirmed booking, frees the seats, starts the refund and emails the contact.
```bash
curl -X POST $API/api/trekker/bookings/<booking-id>/cancel -H "Authorization: Bearer $TOKEN"
```
`200` Booking (`CANCELLED_BY_TREKKER`, with `refunds[0]` when money is due). Errors: `404 BOOKING_NOT_FOUND`, `409 BOOKING_NOT_CANCELLABLE`.

---

## Payments (Razorpay)

Flow: hold → create order → open Razorpay Checkout in the browser → verify → the webhook confirms in the background. The server always takes the amount from the booking.

### POST `/api/trekker/payments/orders`
Creates (or reuses) a Razorpay order for a live hold. Pass the result to Checkout.
```bash
curl -X POST $API/api/trekker/payments/orders -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"booking_id":"<booking-id>"}'
```
`201`
```json
{
  "payment_id": "…-uuid",
  "booking_id": "…-uuid",
  "key_id": "rzp_test_…",
  "razorpay_order_id": "order_N5…",
  "amount_paise": 439800,
  "currency": "INR",
  "description": "Rajmachi · 24 Oct 2026",
  "checkout_timeout_seconds": 480,
  "prefill": { "name": "Asha Rao", "email": "asha@example.com", "contact": "+919876543210" }
}
```
Errors: `400 VALIDATION_FAILED`, `404 BOOKING_NOT_FOUND`, `409 BOOKING_NOT_PAYABLE`, `502 GATEWAY_UNAVAILABLE` (also when Razorpay keys aren't set).

### POST `/api/trekker/payments/{id}/verify`
Checks the signature Checkout returned and records the payment. `{id}` is our `payment_id`.
```bash
curl -X POST $API/api/trekker/payments/<payment-id>/verify -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"razorpay_order_id":"order_N5…","razorpay_payment_id":"pay_N5…","razorpay_signature":"<hex from Checkout>"}'
```
`200` Payment (`PAID`, or still `CREATED` if Razorpay hasn't captured yet — poll GET).
Errors: `400 PAYMENT_SIGNATURE_INVALID`, `404 PAYMENT_NOT_FOUND`, `502 GATEWAY_UNAVAILABLE`.

### GET `/api/trekker/payments/{id}`
Payment status, for polling after Checkout closes.
```bash
curl $API/api/trekker/payments/<payment-id> -H "Authorization: Bearer $TOKEN"
```
`200` Payment. Errors: `404 PAYMENT_NOT_FOUND`.

### POST `/api/webhooks/razorpay`
Called by Razorpay, not the app. The `X-Razorpay-Signature` header must be the HMAC-SHA256 of the raw body with `RAZORPAY_WEBHOOK_SECRET`. Handles `payment.captured`, `order.paid`, `payment.failed`, `refund.processed`, `refund.failed`; repeats are ignored.
```bash
BODY='{"event":"payment.captured","payload":{…}}'
SIG=$(printf '%s' "$BODY" | openssl dgst -sha256 -hmac "$RAZORPAY_WEBHOOK_SECRET" | cut -d' ' -f2)
curl -X POST $API/api/webhooks/razorpay -H 'Content-Type: application/json' \
  -H "X-Razorpay-Signature: $SIG" -H "X-Razorpay-Event-Id: evt_test_1" -d "$BODY"
```
`200` No body (also for duplicates and ignored events). Errors: `400 WEBHOOK_SIGNATURE_INVALID`.

---

## Admin

First admin: sign up, put that email in `ADMIN_EMAILS`, restart the backend, sign in again.

### GET `/api/admin/tracks`
All treks, by name.
```bash
curl $API/api/admin/tracks -H "Authorization: Bearer $ADMIN"
```
`200` `{ "items": [ Track, … ] }` where Track is TrackDetail plus `id`, `created_at`, `updated_at`, with `itinerary` as a plain list of strings.

### POST `/api/admin/tracks`
Creates a trek.

| Field | Rule |
|---|---|
| `slug` | 3–80, lowercase letters/digits and single hyphens, unique |
| `name`, `region` | 1–100 |
| `difficulty` | `EASY`, `MODERATE`, `CHALLENGING` |
| `duration_days` | 1–7 |
| `summary` | 1–200 |
| `description` | 1–5000 |
| `meeting_point` | 1–300 |
| `max_altitude_m`, `base_altitude_m`, `highest_camp_m` | optional, 1–9000; the last two ≤ `max_altitude_m` |
| `distance_km` | optional, 0.1–999.9 |
| `stay` ≤ 120, `season_label` ≤ 60 | optional |
| `itinerary` | empty, or exactly `duration_days` lines of ≤ 200 |

```bash
curl -X POST $API/api/admin/tracks -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' \
  -d '{"slug":"rajmachi","name":"Rajmachi","region":"Lonavala","difficulty":"EASY","duration_days":2,
       "max_altitude_m":830,"summary":"Twin forts above the Ulhas valley","description":"…",
       "meeting_point":"Lonavala station, 6:30 am","distance_km":15.5,"stay":"Tents · twin share",
       "itinerary":["Lonavala to Udhewadi","Forts, then back"]}'
```
`201` Track. Errors: `400 VALIDATION_FAILED`, `409 SLUG_TAKEN`.

### PUT `/api/admin/tracks/{id}`
Edits a trek (same body as POST).
```bash
curl -X PUT $API/api/admin/tracks/<track-id> -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' -d '{…same as POST…}'
```
`200` Track. Errors: `404 TRACK_NOT_FOUND`, `409 SLUG_TAKEN`, `409 TRACK_IN_USE` (changing `duration_days` once departures use it).

### POST `/api/admin/tracks/{id}/photos`
Uploads a trek photo (JPEG/PNG ≤ 5 MB, up to 30 per trek) with an optional caption.
```bash
curl -X POST $API/api/admin/tracks/<track-id>/photos -H "Authorization: Bearer $ADMIN" \
  -F file=@fort.jpg -F caption='Shrivardhan fort'
```
`201`
```json
{ "id": "…-uuid", "url": "http://localhost:8081/api/public/files/track-photos/….jpg", "caption": "Shrivardhan fort" }
```
Errors: `400 UNSUPPORTED_IMAGE`, `400 VALIDATION_FAILED` (`caption`), `404 TRACK_NOT_FOUND`, `409 TOO_MANY_PHOTOS`, `413 FILE_TOO_LARGE`.

### DELETE `/api/admin/tracks/{id}/photos/{photoId}`
Deletes a trek photo.
```bash
curl -X DELETE $API/api/admin/tracks/<track-id>/photos/<photo-id> -H "Authorization: Bearer $ADMIN"
```
`204` No body. Errors: `404 PHOTO_NOT_FOUND`.

### GET `/api/admin/departures`
All departures, newest start date first.
```bash
curl $API/api/admin/departures -H "Authorization: Bearer $ADMIN"
```
`200`
```json
{
  "items": [
    {
      "id": "…-uuid",
      "track": { "id": "…-uuid", "slug": "rajmachi", "name": "Rajmachi", "duration_days": 2 },
      "guide": { "id": "…-uuid", "full_name": "Vikram Shinde", "email": "vikram@example.com", "avatar_url": null },
      "start_date": "2026-10-24", "end_date": "2026-10-25",
      "price_paise": 219900, "max_group_size": 10, "seats_taken": 6,
      "status": "PUBLISHED", "guide_share_bps": 7000,
      "published_at": "2026-09-01T10:00:00Z", "cancelled_at": null,
      "cancel_reason_code": null, "cancel_reason_note": null,
      "created_at": "2026-08-30T10:00:00Z", "updated_at": "2026-09-01T10:00:00Z"
    }
  ]
}
```
`status`: `DRAFT | PUBLISHED | CANCELLED | EXPIRED | COMPLETED`.

### POST `/api/admin/departures`
Creates a draft departure. `price_paise` 10000–10000000 (₹100–₹1,00,000), `max_group_size` 1–10, `start_date` today or later; `end_date` is computed.
```bash
curl -X POST $API/api/admin/departures -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' \
  -d '{"track_id":"<track-id>","guide_id":"<guide-user-id>","start_date":"2026-10-24","price_paise":219900,"max_group_size":10}'
```
`201` AdminDeparture (`DRAFT`). Errors: `400 VALIDATION_FAILED`, `404 TRACK_NOT_FOUND`, `409 NOT_A_GUIDE`.

### PUT `/api/admin/departures/{id}`
Edits a draft (same body as POST).
```bash
curl -X PUT $API/api/admin/departures/<departure-id> -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' -d '{…same as POST…}'
```
`200` AdminDeparture. Errors: `404 DEPARTURE_NOT_FOUND`, `409 DEPARTURE_NOT_DRAFT`, `409 NOT_A_GUIDE`.

### DELETE `/api/admin/departures/{id}`
Deletes a draft.
```bash
curl -X DELETE $API/api/admin/departures/<departure-id> -H "Authorization: Bearer $ADMIN"
```
`204` No body. Errors: `404 DEPARTURE_NOT_FOUND`, `409 DEPARTURE_NOT_DRAFT`.

### POST `/api/admin/departures/{id}/publish`
Publishes a draft so it can be booked, and freezes the guide share on it. The start must be at least a day away.
```bash
curl -X POST $API/api/admin/departures/<departure-id>/publish -H "Authorization: Bearer $ADMIN"
```
`200` AdminDeparture (`PUBLISHED`). Errors: `404 DEPARTURE_NOT_FOUND`, `409 DEPARTURE_NOT_DRAFT`, `409 NOT_A_GUIDE`, `409 START_DATE_TOO_SOON`.

### POST `/api/admin/departures/{id}/cancel`
Force-majeure cancellation, the only way a departure is cancelled. Every paid booking is refunded in full and emailed; open holds are released.

`reason_code`: `WEATHER`, `PERMIT_DENIED`, `GUIDE_UNAVAILABLE`, `SAFETY`. `reason_note`: 1–1000 chars, shown to trekkers.
```bash
curl -X POST $API/api/admin/departures/<departure-id>/cancel -H "Authorization: Bearer $ADMIN" \
  -H 'Content-Type: application/json' -d '{"reason_code":"PERMIT_DENIED","reason_note":"Forest department closed the trail"}'
```
`200` AdminDeparture (`CANCELLED`). Errors: `400 VALIDATION_FAILED`, `404 DEPARTURE_NOT_FOUND`, `409 DEPARTURE_NOT_CANCELLABLE`.

### GET `/api/admin/guides`
All guides.
```bash
curl $API/api/admin/guides -H "Authorization: Bearer $ADMIN"
```
`200`
```json
{ "items": [ { "id": "…-uuid", "full_name": "Vikram Shinde", "email": "vikram@example.com", "phone": "+919822334455", "avatar_url": null, "created_at": "2026-08-01T10:00:00Z" } ] }
```

### POST `/api/admin/guides`
Promotes an existing trekker account to guide, by email. They see the new role after their next token refresh (≤ 15 min).
```bash
curl -X POST $API/api/admin/guides -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' \
  -d '{"email":"vikram@example.com"}'
```
`201` Guide. Errors: `400 VALIDATION_FAILED`, `404 USER_NOT_FOUND`, `409 ALREADY_GUIDE`, `409 ROLE_NOT_PROMOTABLE`.

### GET `/api/admin/insights`
The Insights dashboard: counts and sums only, computed on each request. `days` is the window, 1–365 (default 30). "Accounts" means trekker accounts; days are IST.
```bash
curl "$API/api/admin/insights?days=30" -H "Authorization: Bearer $ADMIN"
```
`200`
```json
{
  "days": 30, "from": "2026-08-26T10:00:00Z", "to": "2026-09-25T10:00:00Z",
  "headline": { "new_accounts": 84, "bookings_held": 41, "bookings_confirmed": 29, "gross_paise": 6377100,
                "hold_to_paid_bps": 7632, "avg_group_size": 2.1, "cancellations": 2 },
  "daily": [ { "date": "2026-08-26", "accounts": 3, "confirmed": 1 } ],
  "funnel": [ { "key": "ACCOUNT", "label": "Signed up", "count": 84 }, { "key": "HELD", "label": "Held seats", "count": 30 },
              { "key": "PAID", "label": "Paid", "count": 22 }, { "key": "TREKKED", "label": "Trekked", "count": 9 },
              { "key": "REVIEWED", "label": "Reviewed", "count": 4 } ],
  "sources": [ { "source": "instagram", "accounts": 40, "confirmed_bookings": 12, "gross_paise": 2638800 } ],
  "campaigns": [ { "source": "kedarkantha_dec", "accounts": 18, "confirmed_bookings": 6, "gross_paise": 1319400 } ],
  "heard_from": [ { "key": "FRIEND_FAMILY", "count": 22 } ],
  "signup_methods": [ { "key": "GUEST_CHECKOUT", "count": 31 } ],
  "devices": [ { "key": "MOBILE", "count": 61 } ],
  "payments": { "attempts": 45, "paid": 30, "failed": 9, "success_bps": 7143,
                "methods": [ { "key": "UPI", "count": 24 } ], "failures": [ { "key": "Bank declined", "count": 5 } ] },
  "treks": [ { "track_id": "…-uuid", "name": "Kedarkantha", "slug": "kedarkantha", "confirmed_bookings": 11,
               "seats": 24, "gross_paise": 2638800, "avg_rating": 4.8, "reviews": 12 } ],
  "upcoming": [ { "departure_id": "…-uuid", "track_name": "Kedarkantha", "start_date": "2026-10-12",
                  "seats_taken": 7, "max_group_size": 10, "guide_name": "Vikram Shinde" } ],
  "guides": [ { "guide_id": "…-uuid", "name": "Vikram Shinde", "departures_completed": 14, "avg_fill_bps": 8214,
                "avg_rating": 4.9, "reviews": 31 } ],
  "marketing_reach": { "accounts": 612, "email": 204, "whatsapp": 188 }
}
```
- `sources` / `campaigns` (top 20 by gross): accounts are counted by first touch, bookings and gross by each booking's last touch. Source = UTM source, else `google-ads` / `meta-ads` from a click id, else the referring site, else `direct`; accounts and bookings from before tracking are `unknown`.
- `funnel` follows one cohort, the trekkers who signed up in the window. `gross_paise` is before refunds.
- `upcoming` covers the next 60 days; `guides` and `marketing_reach` are all-time.

Errors: `400 VALIDATION_FAILED` (`days` out of range).

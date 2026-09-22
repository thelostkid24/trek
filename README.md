# Sahyātri (trek)

A guide-led trekking marketplace: catalog, bookings, and Razorpay payments.

- Backend: Java 21, Spring Boot, PostgreSQL 16 (Flyway-managed schema)
- Frontend: React 19, TypeScript, Vite, Tailwind CSS v4

See [`docs/TRD.md`](docs/TRD.md) for the full technical design (stack, schema, conventions, feature log), and [`docs/API.md`](docs/API.md), [`docs/BACKEND.md`](docs/BACKEND.md), [`docs/FRONTEND.md`](docs/FRONTEND.md), [`docs/DEPLOY.md`](docs/DEPLOY.md) for the rest.

## Prerequisites

- [Docker](https://www.docker.com/) (for the local Postgres database)
- Java 21
- Node.js 20+ and npm
- `backend/mvnw` is checked in — no separate Maven install needed

## 1. Clone and configure

```bash
git clone <repo-url>
cd trek
cp .env.example .env
```

Open `.env` and fill in the values you need. The defaults work for local development as-is; everything else (`RAZORPAY_*`, `GOOGLE_CLIENT_ID`, `MSG91_*`, etc.) can stay blank for local dev — those integrations fall back to `log` providers that just print to the console instead of calling a real provider. Never commit `.env`.

## 2. Start the database

```bash
docker compose up -d
```

This starts Postgres 16 on `localhost:5433` (see `docker-compose.yml`). Data persists in a Docker volume across restarts.

## 3. Run the backend

```bash
cd backend
./mvnw spring-boot:run
```

The API starts on `http://localhost:8081`. It reads `.env` from the repo root automatically (see `backend/src/main/resources/application.yml`) and applies Flyway migrations on startup.

Run backend tests (needs Docker for Testcontainers):

```bash
cd backend
./mvnw test
```

## 4. Run the frontend

```bash
cd frontend
npm install
npm run dev
```

The app starts on `http://localhost:5173` and talks to the backend at `VITE_API_BASE_URL` (defaults to `http://localhost:8081`, set in `.env`).

Other useful frontend commands:

```bash
npm run build   # tsc + vite build
npm run lint    # oxlint
```

## Running both together

Option A — one command, does steps 2-4 for you (starts Postgres, installs frontend deps if needed, runs backend + frontend, logs to `backend.log`/`frontend.log`, Ctrl+C stops both):

```bash
./scripts/dev.sh
```

Option B — run each piece yourself in two terminals, as in steps 2-4 above. Useful when you want to iterate on just one side or watch its output directly.

## Project layout

```
trek/
├── docker-compose.yml   # local Postgres
├── .env.example         # every env var the system reads
├── docs/                # TRD and supporting docs
├── backend/              # Spring Boot API (com.sahyatri.<feature>.{controller,service,repository,entity,dto})
└── frontend/             # React app (src/api, src/pages, src/components, ...)
```

## Troubleshooting

- **Backend can't connect to the database**: make sure `docker compose up -d` is running and `DB_PORT` in `.env` matches `docker-compose.yml` (default `5433`).
- **Flyway validation errors**: never hand-edit an applied migration; the schema is only ever changed by adding a new `V<n>__<feature>.sql` file under `backend/src/main/resources/db/migration`.
- **CORS errors in the browser**: check `CORS_ALLOWED_ORIGINS` in `.env` matches the frontend origin (`http://localhost:5173` by default).

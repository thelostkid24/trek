#!/usr/bin/env bash
# Quickstart: start Postgres, then the backend and frontend together.
# Ctrl+C stops both. Logs go to backend.log / frontend.log at the repo root.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [ ! -f .env ]; then
  echo "No .env found — copying .env.example to .env"
  cp .env.example .env
fi

echo "Starting Postgres (docker compose)..."
docker compose up -d

echo "Waiting for Postgres to be healthy..."
until [ "$(docker compose ps -q postgres | xargs docker inspect -f '{{.State.Health.Status}}' 2>/dev/null)" = "healthy" ]; do
  sleep 1
done

if [ ! -d frontend/node_modules ]; then
  echo "Installing frontend dependencies..."
  npm --prefix frontend install
fi

PIDS=()
cleanup() {
  echo
  echo "Stopping backend and frontend..."
  for pid in "${PIDS[@]}"; do
    kill "$pid" 2>/dev/null || true
  done
  wait 2>/dev/null || true
}
trap cleanup EXIT INT TERM

echo "Starting backend on http://localhost:8081 (log: backend.log)..."
(./backend/mvnw -f backend/pom.xml spring-boot:run > backend.log 2>&1) &
PIDS+=($!)

echo "Starting frontend on http://localhost:5173 (log: frontend.log)..."
(npm --prefix frontend run dev > frontend.log 2>&1) &
PIDS+=($!)

echo
echo "Both running. Press Ctrl+C to stop."
wait

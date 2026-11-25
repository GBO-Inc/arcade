#!/usr/bin/env bash
# Resets the local Arcade stack and seeds the demo users alice and bob.
#
# WARNING: this DELETES the local database volume (pgdata) and all data in it.
# It only affects this Compose project. Run it from anywhere; it changes to the repository root.
#
# Usage:    ./scripts/dev-reset.sh
# Optional: BACKEND_URL (default http://localhost:8080), when the backend is reached elsewhere.
set -euo pipefail

cd "$(dirname "$0")/.."

BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
WAIT_SECONDS=120
DEV_PASSWORD="arcade-dev-password"

if [ ! -f .env ]; then
  echo "Missing .env. Create it first: cp .env.example .env" >&2
  exit 1
fi

echo "Deleting the local database volume and starting a fresh, seeded stack..."
docker compose down -v
ARCADE_SEED_ENABLED=true docker compose up --build -V -d

echo "Waiting for the backend at ${BACKEND_URL} (up to ${WAIT_SECONDS}s)..."
deadline=$((SECONDS + WAIT_SECONDS))
# 401 means the backend answers and requires a token, which is what we want here.
until [ "$(curl -s -o /dev/null -w '%{http_code}' "${BACKEND_URL}/api/rooms" || true)" = "401" ]; do
  if [ "$SECONDS" -ge "$deadline" ]; then
    echo "The backend did not answer within ${WAIT_SECONDS}s. Check: docker compose logs backend" >&2
    exit 1
  fi
  sleep 2
done

echo
echo "Demo accounts (password: ${DEV_PASSWORD}):"
echo "  alice  log in in a normal browser window"
echo "  bob    log in in a private/incognito window"

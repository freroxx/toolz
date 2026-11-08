#!/usr/bin/env bash
# ============================================================================
# Reproducible local runner for the Whisper SQL baseline.
#
# Starts a throwaway postgres:16-alpine, installs the Supabase shim, applies
# supabase/migrations/main-whisper-sql.sql TWICE (fresh build + idempotent
# re-run), then runs the behavioural smoke suite.
#
# Requirements: docker. Nothing here touches a hosted project.
#
#   ./supabase/tests/run-local-smoke.sh
#   POSTGRES_IMAGE=postgres:16-alpine WHISPER_SMOKE_PORT=55433 ./supabase/tests/run-local-smoke.sh
# ============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
IMAGE="${POSTGRES_IMAGE:-postgres:16-alpine}"
PORT="${WHISPER_SMOKE_PORT:-55432}"
NAME="whisper-sql-smoke-$$"

cleanup() {
    docker rm -f "$NAME" >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "==> starting $IMAGE as $NAME on port $PORT"
docker run -d --rm --name "$NAME" \
    -e POSTGRES_PASSWORD=smoke \
    -e POSTGRES_DB=whisper \
    -p "$PORT:5432" \
    "$IMAGE" >/dev/null

for _ in $(seq 1 60); do
    if docker exec "$NAME" pg_isready -U postgres -d whisper >/dev/null 2>&1; then
        break
    fi
    sleep 1
done
docker exec "$NAME" pg_isready -U postgres -d whisper >/dev/null

psql_file() {
    local guc="$1" file="$2"
    docker exec -e PGOPTIONS="-c ${guc}=1" -i "$NAME" \
        psql -U postgres -d whisper -v ON_ERROR_STOP=1 -q < "$file"
}

echo "==> installing Supabase shim"
psql_file whisper.shim "$ROOT/supabase/tests/00_supabase_shim.sql"

echo "==> applying main-whisper-sql.sql (fresh build)"
psql_file whisper.migration "$ROOT/supabase/migrations/main-whisper-sql.sql"

echo "==> applying main-whisper-sql.sql again (idempotent re-run)"
psql_file whisper.migration "$ROOT/supabase/migrations/main-whisper-sql.sql"

echo "==> running behavioural smoke suite"
psql_file whisper.smoke "$ROOT/supabase/tests/main-whisper-smoke.sql"

echo "==> PASS: fresh build, idempotent re-run and behaviour smoke all green"

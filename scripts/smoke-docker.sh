#!/usr/bin/env bash
# Spec B11 (T1): a imagem Docker do backend sobe com o perfil prod e responde /api/health.
# Sobe um Postgres descartável numa rede Docker isolada e remove tudo no final.
# Uso: bash scripts/smoke-docker.sh
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
IMAGE="porganization-api:smoke"
NET="porg-smoke-$$"
DB="porg-smoke-db-$$"
API="porg-smoke-api-$$"
PORT="${SMOKE_PORT:-18080}"
DB_PASS="$(head -c 18 /dev/urandom | base64 | tr -dc 'A-Za-z0-9')"

cleanup() {
  docker rm -f "$API" "$DB" >/dev/null 2>&1 || true
  docker network rm "$NET" >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "== build da imagem"
docker build -q -t "$IMAGE" "$ROOT/backend" >/dev/null

echo "== a imagem não roda como root"
uid="$(docker run --rm --entrypoint id "$IMAGE" -u)"
if [ "$uid" = "0" ]; then echo "FALHA: processo roda como root"; exit 1; fi
echo "ok: uid $uid"

echo "== a imagem não contém .env"
if docker run --rm --entrypoint sh "$IMAGE" -c 'find / -xdev -name ".env" 2>/dev/null | grep -q .'; then
  echo "FALHA: há um .env dentro da imagem"; exit 1
fi
echo "ok"

echo "== sobe Postgres e a API (perfil prod)"
docker network create "$NET" >/dev/null
docker run -d --name "$DB" --network "$NET" -e POSTGRES_PASSWORD="$DB_PASS" postgres:17-alpine >/dev/null
for _ in $(seq 1 30); do
  docker exec "$DB" pg_isready -U postgres >/dev/null 2>&1 && break
  sleep 1
done

docker run -d --name "$API" --network "$NET" -p "$PORT:8080" \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e PORT=8080 \
  -e DB_URL="jdbc:postgresql://$DB:5432/postgres" \
  -e DB_USER=postgres \
  -e DB_PASSWORD="$DB_PASS" \
  -e SUPABASE_JWKS_URI=http://localhost:9/auth/v1/.well-known/jwks.json \
  -e SUPABASE_ISSUER=http://localhost:9/auth/v1 \
  -e FRONTEND_ORIGIN=https://porganization.vercel.app \
  "$IMAGE" >/dev/null

echo "== espera /api/health"
body=""
for _ in $(seq 1 90); do
  body="$(curl -fsS "http://localhost:$PORT/api/health" 2>/dev/null || true)"
  [ -n "$body" ] && break
  if [ "$(docker inspect -f '{{.State.Running}}' "$API")" != "true" ]; then
    echo "FALHA: a API parou"; docker logs "$API" | tail -40; exit 1
  fi
  sleep 1
done

if [[ "$body" != *'"status":"UP"'* ]]; then
  echo "FALHA: /api/health respondeu '$body'"; docker logs "$API" | tail -40; exit 1
fi
echo "ok: /api/health -> $body"

status="$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$PORT/api/me")"
if [ "$status" != "401" ]; then echo "FALHA: /api/me sem token respondeu $status"; exit 1; fi
echo "ok: /api/me sem token -> 401"

echo "imagem OK"

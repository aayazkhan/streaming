#!/usr/bin/env bash
set -euo pipefail

compose_file="${COMPOSE_FILE:-infrastructure/docker/docker-compose.yml}"
api_url="${API_URL:-http://localhost:8080}"

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker smoke: NOT_EXECUTED (docker is unavailable)" >&2
  exit 2
fi

docker compose -f "$compose_file" config --quiet
docker compose -f "$compose_file" up -d --build postgres minio api-gateway media-worker

for endpoint in /health /ready; do
  for attempt in $(seq 1 30); do
    if curl --fail --silent --show-error "$api_url$endpoint" >/dev/null; then
      echo "PASS $endpoint"
      break
    fi
    if [[ "$attempt" == 30 ]]; then
      echo "FAIL $endpoint" >&2
      exit 1
    fi
    sleep 2
  done
done

echo "Docker smoke: PASS"

#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

BACKUP_DIR="${BACKUP_DIR:-$ROOT_DIR/.local/backups}"
mkdir -p "$BACKUP_DIR"
STAMP="$(date +%Y%m%d_%H%M%S)"
BACKUP_FILE="$BACKUP_DIR/foodflow_${STAMP}.dump"

if ! docker compose ps --services --filter status=running | grep -qx postgres; then
  echo "PostgreSQL service is not running. Start it with: docker compose up -d postgres" >&2
  exit 1
fi

docker compose exec -T postgres pg_dump \
  --format=custom \
  --no-owner \
  --no-privileges \
  -U foodflow -d foodflow > "$BACKUP_FILE"

echo "Backup created: $BACKUP_FILE"

#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

BACKUP_FILE="${1:-}"
if [[ -z "$BACKUP_FILE" || ! -f "$BACKUP_FILE" ]]; then
  echo "Usage: scripts/db-restore.sh path/to/foodflow_backup.dump" >&2
  exit 2
fi

if ! docker compose ps --services --filter status=running | grep -qx postgres; then
  echo "PostgreSQL service is not running. Start it with: docker compose up -d postgres" >&2
  exit 1
fi

echo "WARNING: this replaces the current FoodFlow database contents."
docker compose exec -T postgres pg_restore \
  --clean --if-exists \
  --no-owner --no-privileges \
  --single-transaction --exit-on-error \
  -U foodflow -d foodflow < "$BACKUP_FILE"

echo "Database restored from: $BACKUP_FILE"

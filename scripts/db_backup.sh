#!/usr/bin/env bash
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-backups}"
TIMESTAMP="${TIMESTAMP:-$(date -u +%Y%m%d_%H%M%S)}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-blockcred}"
DB_USER="${DB_USER:-blockcred}"
DB_PASSWORD="${DB_PASSWORD:-blockcred}"

mkdir -p "${BACKUP_DIR}"
OUT_FILE="${BACKUP_DIR}/blockcred_${TIMESTAMP}.sql.gz"

export PGPASSWORD="${DB_PASSWORD}"
pg_dump -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" \
  --clean --if-exists --no-owner --no-privileges | gzip > "${OUT_FILE}"
unset PGPASSWORD

sha256sum "${OUT_FILE}" > "${OUT_FILE}.sha256"

echo "Backup created: ${OUT_FILE}"
echo "Checksum file: ${OUT_FILE}.sha256"

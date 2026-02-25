#!/usr/bin/env bash
set -euo pipefail

BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
ADMIN_TOKEN="${ADMIN_TOKEN:-blockcred-admin-dev-token-change-me}"
OUT_DIR="${OUT_DIR:-ops_reports}"
STAMP="$(date -u +%Y%m%d)"

mkdir -p "${OUT_DIR}"

echo "== End-of-Day: Ops Summary =="
curl -s "${BACKEND_URL}/api/ops/summary" \
  -H "X-Admin-Token: ${ADMIN_TOKEN}" | tee "${OUT_DIR}/summary_${STAMP}.json"
echo
echo

echo "== End-of-Day: Active/Failed Jobs =="
curl -s "${BACKEND_URL}/api/ops/jobs?limit=200" \
  -H "X-Admin-Token: ${ADMIN_TOKEN}" | tee "${OUT_DIR}/jobs_${STAMP}.json"
echo
echo

echo "== End-of-Day: Audit Export =="
curl -s "${BACKEND_URL}/api/ops/audit?limit=500" \
  -H "X-Admin-Token: ${ADMIN_TOKEN}" | tee "${OUT_DIR}/audit_${STAMP}.json"
echo
echo

echo "== End-of-Day: Trigger DB Backup =="
./scripts/db_backup.sh

#!/usr/bin/env bash
set -euo pipefail

BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
ADMIN_TOKEN="${ADMIN_TOKEN:-blockcred-admin-dev-token-change-me}"

echo "== Start-of-Day: Health =="
curl -s "${BACKEND_URL}/actuator/health"
echo
echo

echo "== Start-of-Day: Ops Summary =="
curl -s "${BACKEND_URL}/api/ops/summary" \
  -H "X-Admin-Token: ${ADMIN_TOKEN}"
echo
echo

echo "== Start-of-Day: Retryable Queue =="
curl -s "${BACKEND_URL}/api/ops/jobs?status=RETRYABLE&limit=50" \
  -H "X-Admin-Token: ${ADMIN_TOKEN}"
echo
echo

echo "== Start-of-Day: Final Failed Queue =="
curl -s "${BACKEND_URL}/api/ops/jobs?status=FINAL_FAILED&limit=50" \
  -H "X-Admin-Token: ${ADMIN_TOKEN}"
echo
echo

echo "== Start-of-Day: Recent Anomalies =="
curl -s "${BACKEND_URL}/api/ops/anomalies?limit=50" \
  -H "X-Admin-Token: ${ADMIN_TOKEN}"
echo

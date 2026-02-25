#!/usr/bin/env bash
set -euo pipefail

BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
ADMIN_TOKEN="${ADMIN_TOKEN:-blockcred-admin-dev-token-change-me}"

echo "== Health =="
curl -s "${BACKEND_URL}/actuator/health"
echo
echo

echo "== Ops Summary =="
curl -s "${BACKEND_URL}/api/ops/summary" \
  -H "X-Admin-Token: ${ADMIN_TOKEN}"
echo
echo

echo "== Anomalies =="
curl -s "${BACKEND_URL}/api/ops/anomalies?limit=10" \
  -H "X-Admin-Token: ${ADMIN_TOKEN}"
echo

echo "Restore verification checks completed."

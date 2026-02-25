#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <credentialId>"
  exit 1
fi

CREDENTIAL_ID="$1"
BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
ADMIN_TOKEN="${ADMIN_TOKEN:-blockcred-admin-dev-token-change-me}"

echo "== Mid-day Exception: Credential State =="
curl -s "${BACKEND_URL}/api/ops/credentials/${CREDENTIAL_ID}/state" \
  -H "X-Admin-Token: ${ADMIN_TOKEN}"
echo
echo

echo "== Mid-day Exception: Reconcile Attempt =="
curl -s -X POST "${BACKEND_URL}/api/ops/reconcile/${CREDENTIAL_ID}" \
  -H "X-Admin-Token: ${ADMIN_TOKEN}"
echo
echo

echo "== Mid-day Exception: Latest Audit =="
curl -s "${BACKEND_URL}/api/ops/audit?credentialId=${CREDENTIAL_ID}&limit=20" \
  -H "X-Admin-Token: ${ADMIN_TOKEN}"
echo

#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <credentialId>"
  exit 1
fi

BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
ADMIN_TOKEN="${ADMIN_TOKEN:-blockcred-admin-dev-token-change-me}"
CREDENTIAL_ID="$1"

echo "== Credential State =="
curl -s "${BACKEND_URL}/api/ops/credentials/${CREDENTIAL_ID}/state" \
  -H "X-Admin-Token: ${ADMIN_TOKEN}"
echo
echo

echo "== Latest Jobs =="
curl -s "${BACKEND_URL}/api/ops/jobs?limit=10" \
  -H "X-Admin-Token: ${ADMIN_TOKEN}"
echo
echo

echo "== Latest Audit (Credential) =="
curl -s "${BACKEND_URL}/api/ops/audit?credentialId=${CREDENTIAL_ID}&limit=10" \
  -H "X-Admin-Token: ${ADMIN_TOKEN}"
echo

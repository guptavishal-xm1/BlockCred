#!/usr/bin/env bash
set -euo pipefail

BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
ADMIN_TOKEN="${ADMIN_TOKEN:-blockcred-admin-dev-token-change-me}"
DRY_RUN="${DRY_RUN:-true}"

extract_credential_ids() {
  grep -o '"credentialId":"[^"]*"' | sed 's/"credentialId":"//;s/"//'
}

FAILED_JSON=$(curl -s "${BACKEND_URL}/api/ops/jobs?status=FINAL_FAILED&limit=100" \
  -H "X-Admin-Token: ${ADMIN_TOKEN}")

IDS=$(printf "%s" "${FAILED_JSON}" | extract_credential_ids | sort -u)

if [[ -z "${IDS}" ]]; then
  echo "No FINAL_FAILED jobs found."
  exit 0
fi

echo "FINAL_FAILED credentials:"
printf "%s\n" "${IDS}"

if [[ "${DRY_RUN}" == "true" ]]; then
  echo
  echo "DRY_RUN=true, no reconcile calls made."
  exit 0
fi

echo
echo "Triggering reconcile..."
while IFS= read -r cred; do
  [[ -z "${cred}" ]] && continue
  echo "- ${cred}"
  curl -s -X POST "${BACKEND_URL}/api/ops/reconcile/${cred}" \
    -H "X-Admin-Token: ${ADMIN_TOKEN}"
  echo
done <<< "${IDS}"

#!/usr/bin/env bash
set -euo pipefail

BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
ISSUER_TOKEN="${ISSUER_TOKEN:-}"
ISSUER_USERNAME="${ISSUER_USERNAME:-issuer}"
ISSUER_PASSWORD="${ISSUER_PASSWORD:-IssuerPass#2026}"

extract_json_string() {
  local key="$1"
  sed -n "s/.*\"${key}\":\"\\([^\"]*\\)\".*/\\1/p"
}

print_step() {
  printf '\n%s\n' "============================================================"
  printf '%s\n' "$1"
  printf '%s\n' "============================================================"
}

issuer_headers=()
issuer_auth_headers=()
if [[ -n "${ISSUER_TOKEN}" ]]; then
  issuer_headers=(-H "X-Issuer-Token: ${ISSUER_TOKEN}")
  issuer_auth_headers=("${issuer_headers[@]}")
else
  LOGIN_BODY=$(cat <<JSON
{
  "usernameOrEmail": "${ISSUER_USERNAME}",
  "password": "${ISSUER_PASSWORD}"
}
JSON
)
  LOGIN_RESPONSE=$(curl -s -X POST "${BACKEND_URL}/api/auth/login" \
    -H "Content-Type: application/json" \
    -d "${LOGIN_BODY}")
  ACCESS_TOKEN=$(printf '%s' "${LOGIN_RESPONSE}" | extract_json_string "accessToken")
  if [[ -z "${ACCESS_TOKEN}" ]]; then
    echo "Issuer login failed. Set ISSUER_TOKEN for legacy mode or provide ISSUER_USERNAME/ISSUER_PASSWORD."
    echo "Response: ${LOGIN_RESPONSE}"
    exit 1
  fi
  issuer_auth_headers=(-H "Authorization: Bearer ${ACCESS_TOKEN}")
fi

if ! curl -sf "${BACKEND_URL}/actuator/health" >/dev/null 2>&1; then
  if ! curl -sf "${BACKEND_URL}/api/public/verify" >/dev/null 2>&1; then
    echo "Backend not reachable at ${BACKEND_URL}. Start Spring Boot first."
    exit 1
  fi
fi

CRED_ID="CRED-VIVA-$(date +%s)"
NONCE_SUFFIX="$(date +%S)"

ISSUE_PAYLOAD=$(cat <<JSON
{
  "payload": {
    "credentialId": "${CRED_ID}",
    "universityId": "UNI-001",
    "studentId": "STU-001",
    "program": "Computer Science",
    "degree": "B.Tech",
    "issueDate": "2026-02-25",
    "nonce": "550e8400-e29b-41d4-a716-44665544${NONCE_SUFFIX}",
    "version": "1.0"
  }
}
JSON
)

print_step "1) Issue Credential"
ISSUE_RESPONSE=$(curl -s -X POST "${BACKEND_URL}/api/credentials" \
  -H "Content-Type: application/json" \
  "${issuer_auth_headers[@]}" \
  -d "${ISSUE_PAYLOAD}")
printf 'Credential ID: %s\n' "${CRED_ID}"
printf 'Issue response: %s\n' "${ISSUE_RESPONSE}"

print_step "2) Create Share Link"
SHARE_RESPONSE=$(curl -s -X POST "${BACKEND_URL}/api/credentials/${CRED_ID}/share-link" \
  "${issuer_auth_headers[@]}")
VERIFY_URL=$(printf '%s' "${SHARE_RESPONSE}" | extract_json_string "verifyUrl")
TOKEN=$(printf '%s' "${VERIFY_URL}" | sed -n 's/.*[?&]t=\([^&]*\).*/\1/p')
printf 'Share response: %s\n' "${SHARE_RESPONSE}"
printf 'Public verify URL: %s\n' "${VERIFY_URL}"

print_step "3) Verify Immediately (Expected: PENDING_ANCHOR)"
PENDING_RESPONSE=$(curl -s "${BACKEND_URL}/api/public/verify?t=${TOKEN}")
printf '%s\n' "${PENDING_RESPONSE}"

print_step "4) Wait And Verify Again (Expected: VALID)"
sleep 6
VALID_RESPONSE=$(curl -s "${BACKEND_URL}/api/public/verify?t=${TOKEN}")
printf '%s\n' "${VALID_RESPONSE}"

print_step "5) Revoke And Verify (Expected: REVOKED)"
REVOKE_RESPONSE=$(curl -s -X POST "${BACKEND_URL}/api/credentials/${CRED_ID}/revoke" \
  "${issuer_auth_headers[@]}")
REVOKED_RESPONSE=$(curl -s "${BACKEND_URL}/api/public/verify?t=${TOKEN}")
printf 'Revoke response: %s\n' "${REVOKE_RESPONSE}"
printf '%s\n' "${REVOKED_RESPONSE}"

print_step "6) Invalid Token (Expected: Neutral NOT_FOUND)"
UNRESOLVED_RESPONSE=$(curl -s "${BACKEND_URL}/api/public/verify?t=bad.token")
printf '%s\n' "${UNRESOLVED_RESPONSE}"

print_step "Demo Complete"
printf 'Use this URL in browser for live page demo:\n%s\n' "${VERIFY_URL}"

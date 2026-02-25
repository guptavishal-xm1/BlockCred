# BlockCred Verification Core + Public Verifier

Spring Boot reference implementation of BlockCred with a trust-first verification core and a public token-based verifier flow.

## Implemented Features

- Deterministic canonical payload hashing
- Verification API by payload, credential ID, and hash
- Async anchor/revoke worker with retries and reconcile endpoint
- CAS-style job claim, stale `SENT` recovery, and explicit failure codes
- Modular backend services with cache-backed verification
- Public verification endpoint: `/api/public/verify?t=...`
- Minimal HMAC token service for signed verification links
- Share-link endpoint for issuer flow
- Correlation IDs, audit request IDs, and actuator metrics
- Admin ops inspection endpoints for credential/job/audit state
- React + Tailwind frontend:
  - University panel (issue, revoke, reconcile)
  - Employer verifier panel (internal tools)
  - Student wallet panel
  - Public `/verify` page (verdict hero + evidence panel)

## Tech Stack

- Java 17
- Spring Boot 3.3.2
- Spring Web, Validation, JPA, Cache
- Spring Boot Actuator + Micrometer
- H2 (in-memory)
- Caffeine
- React 19 + Vite
- Tailwind CSS 4
- JUnit 5

## Quick Start

```bash
mvn clean test
mvn spring-boot:run
```

Backend URL: `http://localhost:8080`

## Frontend Quick Start

```bash
cd frontend
npm install
npm run dev
```

Frontend URL: `http://localhost:5173`

Notes:

- Vite proxies `/api` to `http://localhost:8080`.
- Backend CORS allows `http://localhost:5173`.

## Auth Headers

- Admin ops endpoints require `X-Admin-Token`.
- Credential mutation endpoints (`issue`, `revoke`, `share-link`) can optionally require `X-Issuer-Token` when `blockcred.auth.issuer-token-enabled=true`.

Dev defaults (from `application.yml`):

- `blockcred.auth.admin-token=blockcred-admin-dev-token-change-me`
- `blockcred.auth.issuer-token=blockcred-issuer-dev-token-change-me`

## API Overview

- `POST /api/credentials`
- `POST /api/credentials/{credentialId}/revoke`
- `POST /api/credentials/{credentialId}/share-link`
- `POST /api/verify/payload`
- `GET /api/verify?credentialId=...`
- `GET /api/verify/hash/{hash}`
- `GET /api/public/verify?t=...`
- `POST /api/ops/reconcile/{credentialId}` (admin-only)
- `GET /api/ops/credentials/{credentialId}/state` (admin-only)
- `GET /api/ops/jobs?status=&limit=` (admin-only)
- `GET /api/ops/audit?credentialId=&limit=` (admin-only)
- `GET /actuator/health`
- `GET /actuator/metrics`
- `GET /actuator/info`

## End-to-End (Internal Flow)

Sample payload:

```json
{
  "credentialId": "CRED-001",
  "universityId": "UNI-001",
  "studentId": "STU-001",
  "program": "Computer Science",
  "degree": "B.Tech",
  "issueDate": "2026-02-25",
  "nonce": "550e8400-e29b-41d4-a716-446655440000",
  "version": "1.0"
}
```

### 1) Issue credential

```bash
curl -s -X POST http://localhost:8080/api/credentials \
  -H "Content-Type: application/json" \
  -H "X-Issuer-Token: blockcred-issuer-dev-token-change-me" \
  -d '{
    "payload": {
      "credentialId": "CRED-001",
      "universityId": "UNI-001",
      "studentId": "STU-001",
      "program": "Computer Science",
      "degree": "B.Tech",
      "issueDate": "2026-02-25",
      "nonce": "550e8400-e29b-41d4-a716-446655440000",
      "version": "1.0"
    }
  }'
```

Golden vector hash for this payload:

`9a1ebcf321a70ac5afd6faa003fbd1590bffbe93546477e53a0a362fdc4c52ba`

### 2) Verify internally (optional)

```bash
curl -s -X POST http://localhost:8080/api/verify/payload \
  -H "Content-Type: application/json" \
  -d '{
    "payload": {
      "credentialId": "CRED-001",
      "universityId": "UNI-001",
      "studentId": "STU-001",
      "program": "Computer Science",
      "degree": "B.Tech",
      "issueDate": "2026-02-25",
      "nonce": "550e8400-e29b-41d4-a716-446655440000",
      "version": "1.0"
    }
  }'
```

## Public Verifier Slice (`/verify?t=`)

### 1) Generate share link

```bash
curl -s -X POST http://localhost:8080/api/credentials/CRED-001/share-link \
  -H "X-Issuer-Token: blockcred-issuer-dev-token-change-me"
```

Response shape:

```json
{
  "verifyUrl": "http://localhost:5173/verify?t=...",
  "tokenExpiresAt": "2026-...Z"
}
```

### 2) Open public verifier page

Open `verifyUrl` in browser.

This renders a standalone public verification page with:

- verdict hero
- honest freshness text derived from backend `checkedAt`
- evidence panel
- secondary technical transaction link
- deterministic “Copy verification summary” action

Note:

- Since anchoring is async, a newly issued credential may briefly show `PENDING_ANCHOR` until the worker confirms chain state (typically a few seconds in this setup).

### 3) Verify via public API directly

```bash
curl -s "http://localhost:8080/api/public/verify?t=<TOKEN>"
```

The public response includes:

- `verificationStatus`
- `verdictHeadline`
- `decisionHint`
- `issuer`
- `checkedAt`
- `credentialHash`
- `blockchainConfirmation`
- `txHash`
- `txExplorerUrl`
- `explanation`
- `referenceContext`

## Viva Demo Script (All Public States)

Run this to demonstrate `PENDING_ANCHOR -> VALID -> REVOKED -> NOT_FOUND` in one flow:

```bash
./scripts/viva_public_verify.sh
```

Optional backend URL override:

```bash
BACKEND_URL=http://localhost:8080 ./scripts/viva_public_verify.sh
```

## Reconcile Endpoint

```bash
curl -s -X POST http://localhost:8080/api/ops/reconcile/CRED-001 \
  -H "X-Admin-Token: blockcred-admin-dev-token-change-me"
```

Typed response example:

`{"result":"NO_ACTIONABLE_JOB","credentialId":"CRED-001","jobType":"ANCHOR","jobStatus":"CONFIRMED","checkedAt":"...","message":"No actionable job: already confirmed"}`

## Ops Inspection Endpoints

```bash
curl -s "http://localhost:8080/api/ops/credentials/CRED-001/state" \
  -H "X-Admin-Token: blockcred-admin-dev-token-change-me"

curl -s "http://localhost:8080/api/ops/jobs?status=FINAL_FAILED&limit=20" \
  -H "X-Admin-Token: blockcred-admin-dev-token-change-me"

curl -s "http://localhost:8080/api/ops/audit?credentialId=CRED-001&limit=20" \
  -H "X-Admin-Token: blockcred-admin-dev-token-change-me"
```

## Ops Scripts

```bash
chmod +x scripts/ops_state.sh scripts/ops_retry_failed.sh

./scripts/ops_state.sh CRED-001
DRY_RUN=true ./scripts/ops_retry_failed.sh
DRY_RUN=false ./scripts/ops_retry_failed.sh
```

## Sandbox Debug Utility

Run:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--blockcred.sandbox.enabled=true
```

It prints canonical JSON, computed hash, DB/chain lookup inputs, and resolver verdict.

## Ops Runbook (Quick Recovery)

1. If chain/RPC is unavailable:
- verify with `/api/public/verify?t=...` or `/api/verify/hash/{hash}` and expect controlled `CHAIN_UNAVAILABLE`/`PENDING_ANCHOR`.
- inspect job backlog with `/api/ops/jobs?status=RETRYABLE`.

2. If jobs reach `FINAL_FAILED`:
- inspect credential/job/audit with `./scripts/ops_state.sh <credentialId>`.
- queue retries in controlled mode with `DRY_RUN=false ./scripts/ops_retry_failed.sh`.

3. If reconcile returns cooldown/no-actionable:
- read typed `result` and `message` from reconcile response.
- check latest audit entries via `/api/ops/audit`.

## Verification Statuses

- `VALID`
- `REVOKED`
- `NOT_FOUND`
- `TAMPERED`
- `PENDING_ANCHOR`
- `CHAIN_UNAVAILABLE`

## Notes

- Current blockchain adapter is `InMemoryBlockchainGateway` for deterministic local behavior.
- Cache key is always `credentialHash` for internal verify paths.
- Public `/api/public/verify` intentionally uses live verification path (no cache shortcut).

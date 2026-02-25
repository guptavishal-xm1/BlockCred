# BlockCred Institutional Readiness Build

BlockCred is a modular-monolith Spring Boot + React system for blockchain-backed academic credential issuance and verification.

This build is focused on operational readiness:
- reliable async anchor/revoke job handling
- typed admin reconcile + ops visibility endpoints
- public token verifier for employer trust checks
- wallet kill switch and compromise-safe recovery flow
- audit integrity with daily digest and retention policies
- deployable `pilot` profile for single-VM + Docker + Postgres

## Implemented Capabilities

- Deterministic canonical payload hashing and verification resolver
- Verification API by payload, credential ID, and hash
- Async anchoring and revocation with:
  - claim-safe processing
  - retry with failure codes
  - stale `SENT` recovery
  - admin reconcile with cooldown handling
- Public verification endpoint `/api/public/verify?t=...`
- Signed share links via minimal HMAC token service
- Admin ops endpoints for:
  - credential state
  - job queue/failures
  - audit timeline
  - summary dashboard data
  - anomalies feed
  - wallet controls
- Correlation ID propagation (`X-Request-Id`)
- Audit enrichment (`severity`, `category`, `requestId`)
- Daily audit digest generation (tamper-evidence artifact)
- Audit retention cleanup scheduler
- Operator SOP scripts (start/mid/end day)
- DB backup/restore scripts

## Tech Stack

- Java 17
- Spring Boot 3.3.2
- Spring Web, Validation, Data JPA, Cache, Actuator
- Flyway
- H2 (dev/test)
- PostgreSQL (pilot)
- Caffeine
- React + Vite + Tailwind
- JUnit 5

## Runtime Profiles

- `dev`:
  - H2 in-memory DB
  - in-memory blockchain adapter
  - fast local iteration
- `pilot`:
  - PostgreSQL
  - Flyway migrations + `ddl-auto=validate`
  - strict secret requirements
- `test`:
  - isolated H2 configuration for tests

Files:
- `src/main/resources/application.yml`
- `src/main/resources/application-dev.yml`
- `src/main/resources/application-pilot.yml`
- `src/main/resources/application-test.yml`

## Local Quick Start

### Backend

```bash
mvn clean test
mvn spring-boot:run
```

Backend: `http://localhost:8080`

### Frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend: `http://localhost:5173`

## Pilot Deployment (Single VM + Docker)

1. Copy env template:

```bash
cp .env.example .env
```

2. Fill required secrets in `.env`:
- `BLOCKCRED_ADMIN_TOKEN`
- `BLOCKCRED_ISSUER_TOKEN`
- `BLOCKCRED_PUBLIC_TOKEN_SECRET`
- `BLOCKCRED_AUDIT_DIGEST_SECRET`
- wallet key settings (`BLOCKCRED_WALLET_KEY_SOURCE` + key value/path)

3. Start stack:

```bash
docker compose --env-file .env up --build -d
```

4. Verify health:

```bash
curl -s http://localhost:8080/actuator/health
```

## Security Headers

- Admin ops endpoints: `X-Admin-Token`
- Issuer mutation endpoints: `X-Issuer-Token` (enabled when `blockcred.auth.issuer-token-enabled=true`)
- Correlation: optional `X-Request-Id`

## API Overview

### Credential APIs

- `POST /api/credentials`
- `POST /api/credentials/{credentialId}/revoke`
- `POST /api/credentials/{credentialId}/share-link`

### Verification APIs

- `POST /api/verify/payload`
- `GET /api/verify?credentialId=...`
- `GET /api/verify/hash/{hash}`
- `GET /api/public/verify?t=...`

### Ops APIs (Admin)

- `POST /api/ops/reconcile/{credentialId}`
- `GET /api/ops/credentials/{credentialId}/state`
- `GET /api/ops/jobs?status=&limit=`
- `GET /api/ops/audit?credentialId=&limit=`
- `GET /api/ops/summary`
- `GET /api/ops/anomalies?limit=`
- `GET /api/ops/wallet/status`
- `POST /api/ops/wallet/disable`
- `POST /api/ops/wallet/enable`

### Actuator

- `GET /actuator/health`
- `GET /actuator/metrics`
- `GET /actuator/info`

## End-to-End Internal Flow (cURL)

### Issue

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

### Reconcile (typed response)

```bash
curl -s -X POST http://localhost:8080/api/ops/reconcile/CRED-001 \
  -H "X-Admin-Token: blockcred-admin-dev-token-change-me"
```

Response includes:
- `result`
- `message`
- `recommendedAction`
- `cooldownRemainingSeconds` (when cooldown active)

## Public Verifier Demo

Run full public flow (pending -> valid -> revoked -> unresolved):

```bash
./scripts/viva_public_verify.sh
```

Optional environment:

```bash
BACKEND_URL=http://localhost:8080 ISSUER_TOKEN=blockcred-issuer-dev-token-change-me ./scripts/viva_public_verify.sh
```

## Operator SOP Scripts

### Start-of-day check

```bash
ADMIN_TOKEN=blockcred-admin-dev-token-change-me ./scripts/ops_start_of_day.sh
```

### Mid-day exception handling

```bash
ADMIN_TOKEN=blockcred-admin-dev-token-change-me ./scripts/ops_midday_exception.sh CRED-001
```

### End-of-day closure

```bash
ADMIN_TOKEN=blockcred-admin-dev-token-change-me ./scripts/ops_end_of_day.sh
```

### Credential deep inspection

```bash
ADMIN_TOKEN=blockcred-admin-dev-token-change-me ./scripts/ops_state.sh CRED-001
```

### Retry final failed jobs

```bash
ADMIN_TOKEN=blockcred-admin-dev-token-change-me DRY_RUN=true ./scripts/ops_retry_failed.sh
ADMIN_TOKEN=blockcred-admin-dev-token-change-me DRY_RUN=false ./scripts/ops_retry_failed.sh
```

## Wallet Safeguard Operations

### Status

```bash
curl -s http://localhost:8080/api/ops/wallet/status \
  -H "X-Admin-Token: blockcred-admin-dev-token-change-me"
```

### Disable (compromise-safe mode)

```bash
curl -s -X POST http://localhost:8080/api/ops/wallet/disable \
  -H "X-Admin-Token: blockcred-admin-dev-token-change-me" \
  -H "Content-Type: application/json" \
  -d '{"reason":"Incident response"}'
```

### Enable

```bash
curl -s -X POST http://localhost:8080/api/ops/wallet/enable \
  -H "X-Admin-Token: blockcred-admin-dev-token-change-me"
```

When disabled, chain submission is blocked and jobs remain retryable with `failureCode=WALLET_DISABLED`.

## Backup and Restore

### Backup

```bash
DB_HOST=localhost DB_PORT=5432 DB_NAME=blockcred DB_USER=blockcred DB_PASSWORD=blockcred ./scripts/db_backup.sh
```

### Restore

```bash
DB_HOST=localhost DB_PORT=5432 DB_NAME=blockcred DB_USER=blockcred DB_PASSWORD=blockcred ./scripts/db_restore.sh backups/blockcred_<timestamp>.sql.gz
```

### Restore verification

```bash
BACKEND_URL=http://localhost:8080 ADMIN_TOKEN=blockcred-admin-dev-token-change-me ./scripts/db_restore_verify.sh
```

## Recovery Playbooks

### RPC outage

- Expected: verification returns controlled `CHAIN_UNAVAILABLE` or `PENDING_ANCHOR`.
- Recovery: automatic retries + manual reconcile.

### Worker outage

- Expected: queue accumulates without loss.
- Recovery: restart service; worker drains backlog via bounded batches.

### Wallet compromise

1. Disable wallet.
2. Rotate wallet key + token secrets.
3. Restart service.
4. Reconcile backlog.
5. Export audit timeline.

### DB restore

- Restore DB dump.
- Verify health, summary, anomalies.
- Run a known credential verification check.

## Audit Integrity

- Audit records include actor, request ID, category, severity, details.
- Daily digest job computes HMAC digest for each UTC day.
- Digest stored in `audit_digests` with record count.
- Retention policy cleanup is scheduled and configurable.

## Sandbox Utility

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--blockcred.sandbox.enabled=true
```

Prints canonical JSON, hash, DB/chain inputs, verdict, and latest job snapshot.

## Notes

- Current blockchain adapter is `InMemoryBlockchainGateway` for deterministic local behavior.
- Public verification path uses live verification (no cache shortcut) by design.
- Cache key remains `credentialHash` for internal verification caches.

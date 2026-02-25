# BlockCred Verification Core + UI

Spring Boot reference implementation of the BlockCred verification core:

- deterministic canonical payload hashing
- payload-first verification (`/api/verify/payload`)
- lifecycle state machine with guarded transitions
- async anchor/revoke job worker with retries
- reconcile endpoint with admin guard + cooldown
- hash-keyed verification cache (Caffeine)
- sandbox runner for verification debugging

React + Tailwind frontend includes:

- University panel (issue, revoke, reconcile)
- Employer verifier panel (payload, credential ID, hash verification)
- Student wallet panel (local credential list + quick actions)

## Tech Stack

- Java 17
- Spring Boot 3.3.2
- Spring Web, Validation, JPA, Cache
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

Default app URL: `http://localhost:8080`

## Frontend Quick Start

```bash
cd frontend
npm install
npm run dev
```

Frontend URL: `http://localhost:5173`

Notes:

- Vite proxies `/api` to `http://localhost:8080`.
- Backend CORS is also enabled for `http://localhost:5173`.

## API Overview

- `POST /api/credentials`
- `POST /api/credentials/{credentialId}/revoke`
- `POST /api/verify/payload`
- `GET /api/verify?credentialId=...`
- `GET /api/verify/hash/{hash}`
- `POST /api/ops/reconcile/{credentialId}` (admin-only, requires `X-Admin: true`)

## End-to-End Example

Use this sample canonical payload:

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

### 1) Issue credential (queues anchoring job)

```bash
curl -s -X POST http://localhost:8080/api/credentials \
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

Expected hash for the sample payload (golden vector):

`9a1ebcf321a70ac5afd6faa003fbd1590bffbe93546477e53a0a362fdc4c52ba`

### 2) Verify by payload

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

### 3) Verify by credential ID

```bash
curl -s "http://localhost:8080/api/verify?credentialId=CRED-001"
```

### 4) Verify by hash

```bash
curl -s "http://localhost:8080/api/verify/hash/9a1ebcf321a70ac5afd6faa003fbd1590bffbe93546477e53a0a362fdc4c52ba"
```

### 5) Revoke credential

```bash
curl -s -X POST http://localhost:8080/api/credentials/CRED-001/revoke
```

### 6) Manual reconcile (admin)

```bash
curl -s -X POST http://localhost:8080/api/ops/reconcile/CRED-001 \
  -H "X-Admin: true"
```

If revoke/anchor is already confirmed by the worker, reconcile correctly returns:

`{"error":"No actionable job: already confirmed"}`

## Sandbox Debugging Utility

The sandbox runner prints:

- canonical JSON
- computed hash
- DB lookup state
- chain lookup state
- final verdict + explanation

Run it with:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--blockcred.sandbox.enabled=true
```

## Verification Statuses

- `VALID`
- `REVOKED`
- `NOT_FOUND`
- `TAMPERED`
- `PENDING_ANCHOR`
- `CHAIN_UNAVAILABLE`

## Notes

- Current blockchain adapter is `InMemoryBlockchainGateway` for deterministic local behavior.
- Cache key is always `credentialHash`.
- Reconcile endpoint enforces admin header and cooldown.

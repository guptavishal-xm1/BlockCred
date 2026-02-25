package com.blockcred.api;

import java.time.Instant;

public record OpsAuditEntryResponse(
        Long id,
        String action,
        String credentialId,
        String actor,
        String requestId,
        String details,
        Instant createdAt
) {
}

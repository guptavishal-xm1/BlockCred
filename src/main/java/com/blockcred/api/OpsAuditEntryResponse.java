package com.blockcred.api;

import com.blockcred.domain.AuditCategory;
import com.blockcred.domain.AuditSeverity;

import java.time.Instant;

public record OpsAuditEntryResponse(
        Long id,
        String action,
        String credentialId,
        String actor,
        AuditSeverity severity,
        AuditCategory category,
        String requestId,
        String details,
        Instant createdAt
) {
}

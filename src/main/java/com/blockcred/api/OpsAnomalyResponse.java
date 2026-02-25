package com.blockcred.api;

import com.blockcred.domain.AuditCategory;
import com.blockcred.domain.AuditSeverity;

import java.time.Instant;

public record OpsAnomalyResponse(
        Long id,
        String action,
        String credentialId,
        AuditSeverity severity,
        AuditCategory category,
        String details,
        Instant createdAt,
        String recommendedAction
) {
}

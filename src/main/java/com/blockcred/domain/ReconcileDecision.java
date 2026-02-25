package com.blockcred.domain;

import java.time.Instant;

public record ReconcileDecision(
        ReconcileResultCode result,
        String credentialId,
        JobType jobType,
        JobStatus jobStatus,
        Instant checkedAt,
        String message,
        String recommendedAction,
        Long cooldownRemainingSeconds
) {
}

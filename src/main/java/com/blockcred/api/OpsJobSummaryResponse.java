package com.blockcred.api;

import com.blockcred.domain.JobFailureCode;
import com.blockcred.domain.JobStatus;
import com.blockcred.domain.JobType;

import java.time.Instant;

public record OpsJobSummaryResponse(
        Long jobId,
        String credentialId,
        String hash,
        JobType jobType,
        JobStatus status,
        int retryCount,
        Instant nextRunAt,
        Instant lastAttemptAt,
        JobFailureCode failureCode,
        String lastError,
        Instant lastManualTriggerAt,
        Instant createdAt,
        Instant updatedAt
) {
}

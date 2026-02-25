package com.blockcred.api;

import com.blockcred.domain.JobStatus;
import com.blockcred.domain.JobType;
import com.blockcred.domain.ReconcileResultCode;

import java.time.Instant;

public record ReconcileResultResponse(
        ReconcileResultCode result,
        String credentialId,
        JobType jobType,
        JobStatus jobStatus,
        Instant checkedAt,
        String message
) {
}

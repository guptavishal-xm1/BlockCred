package com.blockcred.api;

import com.blockcred.domain.CredentialLifecycleStatus;

import java.time.Instant;

public record OpsCredentialStateResponse(
        String credentialId,
        String hash,
        CredentialLifecycleStatus lifecycleStatus,
        String lastTxHash,
        Instant updatedAt,
        OpsJobSummaryResponse latestJob
) {
}

package com.blockcred.api;

import java.time.Instant;
import java.time.LocalDate;

public record OpsSummaryResponse(
        long pendingCount,
        long retryableCount,
        long finalFailedCount,
        Long maxPendingAgeMinutes,
        Long maxRetryableAgeMinutes,
        Long maxFinalFailedAgeMinutes,
        Instant lastWorkerRunAt,
        boolean chainReachable,
        long recentAnomalyCount,
        LocalDate latestAuditDigestDate,
        Integer latestAuditDigestRecordCount,
        boolean pendingAgeAlert,
        boolean retryThresholdAlert,
        boolean finalFailedAlert,
        boolean revocationPropagationAlert
) {
}

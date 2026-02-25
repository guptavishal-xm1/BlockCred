package com.blockcred.api;

import java.time.Instant;

public record OpsSecurityStatusResponse(
        long lockedAccountCount,
        long disabledAccountCount,
        long failedLoginsLast24h,
        long activeRefreshSessions,
        boolean legacyHeaderAuthEnabled,
        Instant checkedAt
) {
}

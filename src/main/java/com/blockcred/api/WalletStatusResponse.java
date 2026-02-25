package com.blockcred.api;

import java.time.Instant;

public record WalletStatusResponse(
        boolean enabled,
        String keySource,
        boolean keyPresent,
        String disableReason,
        String updatedBy,
        Instant updatedAt
) {
}

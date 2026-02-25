package com.blockcred.api;

import java.time.Instant;

public record WalletControlResponse(
        String result,
        boolean enabled,
        String message,
        String updatedBy,
        Instant updatedAt
) {
}

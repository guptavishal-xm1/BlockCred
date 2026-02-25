package com.blockcred.api;

import java.time.Instant;

public record ShareLinkResponse(
        String verifyUrl,
        Instant tokenExpiresAt
) {
}

package com.blockcred.api;

import java.time.Instant;

public record AuthSessionResponse(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt,
        AuthUserResponse user
) {
}

package com.blockcred.api;

import jakarta.validation.constraints.NotBlank;

public record AuthLoginRequest(
        @NotBlank String usernameOrEmail,
        @NotBlank String password
) {
}

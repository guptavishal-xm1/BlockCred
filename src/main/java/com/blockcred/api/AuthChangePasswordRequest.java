package com.blockcred.api;

import jakarta.validation.constraints.NotBlank;

public record AuthChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank String newPassword
) {
}

package com.blockcred.api;

import com.blockcred.domain.AuthRole;
import com.blockcred.domain.AuthUserStatus;

import java.util.Set;

public record AuthUserResponse(
        Long id,
        String displayName,
        String username,
        String email,
        Set<AuthRole> roles,
        AuthUserStatus status,
        boolean forcePasswordChange
) {
}

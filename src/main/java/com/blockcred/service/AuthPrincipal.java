package com.blockcred.service;

import com.blockcred.domain.AuthRole;

import java.util.Set;

public record AuthPrincipal(
        Long userId,
        String username,
        String displayName,
        Set<AuthRole> roles,
        boolean legacy
) {
}

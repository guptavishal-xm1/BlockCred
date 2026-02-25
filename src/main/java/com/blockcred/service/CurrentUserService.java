package com.blockcred.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CurrentUserService {
    public Optional<AuthPrincipal> currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return Optional.empty();
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof AuthPrincipal authPrincipal) {
            return Optional.of(authPrincipal);
        }
        return Optional.empty();
    }

    public String actorOr(String fallback) {
        return currentPrincipal().map(AuthPrincipal::username).orElse(fallback);
    }

    public AuthPrincipal requireAuthenticated() {
        return currentPrincipal().orElseThrow(() -> new IllegalStateException("Authenticated user required"));
    }
}

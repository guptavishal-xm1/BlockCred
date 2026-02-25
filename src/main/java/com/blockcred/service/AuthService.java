package com.blockcred.service;

import com.blockcred.api.AuthSessionResponse;
import com.blockcred.api.AuthUserResponse;
import com.blockcred.domain.AuthRole;
import com.blockcred.domain.AuthUserStatus;
import com.blockcred.infra.AuthUserEntity;
import com.blockcred.infra.AuthUserRepository;
import com.blockcred.infra.AuthUserRoleEntity;
import com.blockcred.infra.AuthUserRoleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuthService {
    private final AuthUserRepository authUserRepository;
    private final AuthUserRoleRepository authUserRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyService passwordPolicyService;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;
    private final Clock clock;
    private final int lockThreshold;
    private final int lockMinutes;

    public AuthService(
            AuthUserRepository authUserRepository,
            AuthUserRoleRepository authUserRoleRepository,
            PasswordEncoder passwordEncoder,
            PasswordPolicyService passwordPolicyService,
            JwtTokenService jwtTokenService,
            RefreshTokenService refreshTokenService,
            CurrentUserService currentUserService,
            AuditService auditService,
            Clock clock,
            @Value("${blockcred.auth.login.lock-threshold:5}") int lockThreshold,
            @Value("${blockcred.auth.login.lock-minutes:15}") int lockMinutes
    ) {
        this.authUserRepository = authUserRepository;
        this.authUserRoleRepository = authUserRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicyService = passwordPolicyService;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
        this.clock = clock;
        this.lockThreshold = lockThreshold;
        this.lockMinutes = lockMinutes;
    }

    @Transactional
    public AuthSessionResponse login(String usernameOrEmail, String password, String ip, String userAgent) {
        AuthUserEntity user = authUserRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(usernameOrEmail, usernameOrEmail)
                .orElse(null);
        if (user == null) {
            auditService.log("AUTH_LOGIN_FAILED", "SYSTEM", "auth", "unknown user");
            throw unauthorized("Invalid credentials");
        }

        Instant now = clock.instant();
        if (user.getStatus() == AuthUserStatus.DISABLED) {
            auditService.log("AUTH_LOGIN_FAILED", "SYSTEM", "auth", "disabled user=" + user.getUsername());
            throw unauthorized("Invalid credentials");
        }

        if (user.getStatus() == AuthUserStatus.LOCKED && user.getLockedUntil() != null && user.getLockedUntil().isBefore(now)) {
            user.setStatus(AuthUserStatus.ACTIVE);
            user.setLockedUntil(null);
            user.setFailedLoginCount(0);
        }

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            auditService.log("AUTH_LOGIN_FAILED", "SYSTEM", "auth", "locked user=" + user.getUsername());
            throw unauthorized("Invalid credentials");
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            int failed = user.getFailedLoginCount() + 1;
            user.setFailedLoginCount(failed);
            if (failed >= lockThreshold) {
                user.setStatus(AuthUserStatus.LOCKED);
                user.setLockedUntil(now.plus(lockMinutes, ChronoUnit.MINUTES));
                auditService.log("AUTH_ACCOUNT_LOCKED", "SYSTEM", "auth", "user=" + user.getUsername());
            }
            authUserRepository.save(user);
            auditService.log("AUTH_LOGIN_FAILED", "SYSTEM", "auth", "bad password user=" + user.getUsername());
            throw unauthorized("Invalid credentials");
        }

        user.setStatus(AuthUserStatus.ACTIVE);
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        authUserRepository.save(user);

        Set<AuthRole> roles = rolesForUser(user.getId());
        AuthPrincipal principal = new AuthPrincipal(user.getId(), user.getUsername(), user.getDisplayName(), roles, false);
        JwtTokenService.SignedAccessToken access = jwtTokenService.sign(principal);
        RefreshTokenService.IssuedRefreshToken refresh = refreshTokenService.issue(user.getId(), ip, userAgent);
        auditService.log("AUTH_LOGIN_SUCCESS", "SYSTEM", user.getUsername(), "roles=" + roles);
        return new AuthSessionResponse(access.token(), access.expiresAt(), refresh.token(), refresh.expiresAt(), toUserResponse(user, roles));
    }

    @Transactional
    public AuthSessionResponse refresh(String refreshToken, String ip, String userAgent) {
        RefreshTokenService.IssuedRefreshToken rotated = refreshTokenService.rotate(refreshToken, null, ip, userAgent)
                .orElseThrow(() -> unauthorized("Unauthorized"));
        AuthUserEntity user = authUserRepository.findById(rotated.userId())
                .orElseThrow(() -> unauthorized("Unauthorized"));
        if (user.getStatus() == AuthUserStatus.DISABLED) {
            refreshTokenService.revokeAllByUserId(user.getId());
            throw unauthorized("Unauthorized");
        }

        Set<AuthRole> roles = rolesForUser(user.getId());
        AuthPrincipal principal = new AuthPrincipal(user.getId(), user.getUsername(), user.getDisplayName(), roles, false);
        JwtTokenService.SignedAccessToken access = jwtTokenService.sign(principal);
        auditService.log("AUTH_REFRESH_SUCCESS", "SYSTEM", user.getUsername(), "token rotated");
        return new AuthSessionResponse(access.token(), access.expiresAt(), rotated.token(), rotated.expiresAt(), toUserResponse(user, roles));
    }

    @Transactional
    public void logout(String refreshToken) {
        AuthPrincipal principal = currentUserService.currentPrincipal().orElse(null);
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenService.revoke(refreshToken);
        } else if (principal != null && principal.userId() != null && principal.userId() > 0) {
            refreshTokenService.revokeAllByUserId(principal.userId());
        }
        String actor = principal == null ? "auth" : principal.username();
        auditService.log("AUTH_LOGOUT", "SYSTEM", actor, "logout");
    }

    @Transactional(readOnly = true)
    public AuthUserResponse me() {
        AuthPrincipal principal = currentUserService.currentPrincipal()
                .orElseThrow(() -> unauthorized("Unauthorized"));

        if (principal.legacy()) {
            return new AuthUserResponse(
                    -1L,
                    principal.displayName(),
                    principal.username(),
                    "",
                    principal.roles(),
                    AuthUserStatus.ACTIVE,
                    false
            );
        }

        AuthUserEntity user = authUserRepository.findById(principal.userId())
                .orElseThrow(() -> unauthorized("Unauthorized"));
        return toUserResponse(user, rolesForUser(user.getId()));
    }

    @Transactional
    public void changePassword(String currentPassword, String newPassword) {
        AuthPrincipal principal = currentUserService.currentPrincipal()
                .orElseThrow(() -> unauthorized("Unauthorized"));
        if (principal.legacy() || principal.userId() == null || principal.userId() <= 0) {
            throw unauthorized("Unauthorized");
        }
        AuthUserEntity user = authUserRepository.findById(principal.userId())
                .orElseThrow(() -> unauthorized("Unauthorized"));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw unauthorized("Unauthorized");
        }
        passwordPolicyService.validate(newPassword);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setForcePasswordChange(false);
        authUserRepository.save(user);
        refreshTokenService.revokeAllByUserId(user.getId());
        auditService.log("AUTH_PASSWORD_CHANGED", "SYSTEM", user.getUsername(), "password rotated");
    }

    @Transactional(readOnly = true)
    public long lockedAccountCount() {
        return authUserRepository.countByLockedUntilAfter(clock.instant());
    }

    @Transactional(readOnly = true)
    public long disabledAccountCount() {
        return authUserRepository.countByStatus(AuthUserStatus.DISABLED);
    }

    @Transactional(readOnly = true)
    public long activeRefreshSessions() {
        return refreshTokenService.activeSessionCount();
    }

    private Set<AuthRole> rolesForUser(Long userId) {
        return authUserRoleRepository.findByUserId(userId).stream()
                .map(AuthUserRoleEntity::getRole)
                .collect(Collectors.toSet());
    }

    private AuthUserResponse toUserResponse(AuthUserEntity user, Set<AuthRole> roles) {
        return new AuthUserResponse(
                user.getId(),
                user.getDisplayName(),
                user.getUsername(),
                user.getEmail(),
                roles,
                user.getStatus(),
                user.isForcePasswordChange()
        );
    }

    private ResponseStatusException unauthorized(String message) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
    }
}

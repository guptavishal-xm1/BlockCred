package com.blockcred.service;

import com.blockcred.infra.AuthRefreshTokenEntity;
import com.blockcred.infra.AuthRefreshTokenRepository;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class RefreshTokenService {
    private final AuthRefreshTokenRepository refreshTokenRepository;
    private final AuditService auditService;
    private final Clock clock;
    private final long refreshTokenTtlDays;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(
            AuthRefreshTokenRepository refreshTokenRepository,
            AuditService auditService,
            Clock clock,
            @Value("${blockcred.auth.refresh-token-ttl-days:7}") long refreshTokenTtlDays
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.auditService = auditService;
        this.clock = clock;
        this.refreshTokenTtlDays = refreshTokenTtlDays;
    }

    @Transactional
    public IssuedRefreshToken issue(Long userId, String createdIp, String userAgent) {
        String raw = generateRawToken();
        String tokenHash = sha256Hex(raw);
        Instant now = clock.instant();
        Instant expiresAt = now.plus(refreshTokenTtlDays, ChronoUnit.DAYS);

        AuthRefreshTokenEntity entity = new AuthRefreshTokenEntity();
        entity.setUserId(userId);
        entity.setTokenHash(tokenHash);
        entity.setIssuedAt(now);
        entity.setExpiresAt(expiresAt);
        entity.setCreatedIpHash(sha256Hex(createdIp));
        entity.setUserAgentHash(sha256Hex(userAgent));
        entity.setRequestId(MDC.get("requestId"));
        refreshTokenRepository.save(entity);
        return new IssuedRefreshToken(userId, raw, expiresAt);
    }

    @Transactional
    public Optional<IssuedRefreshToken> rotate(String rawToken, Long expectedUserId, String ip, String userAgent) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        String tokenHash = sha256Hex(rawToken);
        Optional<AuthRefreshTokenEntity> found = refreshTokenRepository.findByTokenHash(tokenHash);
        if (found.isEmpty()) {
            return Optional.empty();
        }

        AuthRefreshTokenEntity current = found.get();
        Instant now = clock.instant();

        if (expectedUserId != null && !expectedUserId.equals(current.getUserId())) {
            return Optional.empty();
        }

        if (current.getRevokedAt() != null) {
            if (current.getReplacedByTokenHash() != null && !current.getReplacedByTokenHash().isBlank()) {
                refreshTokenRepository.revokeActiveByUserId(current.getUserId(), now);
                auditService.log("AUTH_REFRESH_REUSE_DETECTED", "SYSTEM", "auth", "userId=" + current.getUserId());
            }
            return Optional.empty();
        }

        if (current.getExpiresAt().isBefore(now)) {
            current.setRevokedAt(now);
            refreshTokenRepository.save(current);
            return Optional.empty();
        }

        IssuedRefreshToken next = issue(current.getUserId(), ip, userAgent);
        current.setRevokedAt(now);
        current.setReplacedByTokenHash(sha256Hex(next.token()));
        refreshTokenRepository.save(current);
        return Optional.of(next);
    }

    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        String tokenHash = sha256Hex(rawToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(clock.instant());
                refreshTokenRepository.save(token);
            }
        });
    }

    @Transactional
    public int revokeAllByUserId(Long userId) {
        return refreshTokenRepository.revokeActiveByUserId(userId, clock.instant());
    }

    @Transactional(readOnly = true)
    public long activeSessionCount() {
        return refreshTokenRepository.countByRevokedAtIsNullAndExpiresAtAfter(clock.instant());
    }

    @Transactional
    public int purgeExpired() {
        return refreshTokenRepository.deleteByExpiresAtBefore(clock.instant());
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] data = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(data);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash value", e);
        }
    }

    public record IssuedRefreshToken(Long userId, String token, Instant expiresAt) {
    }
}

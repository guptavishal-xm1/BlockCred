package com.blockcred.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface AuthRefreshTokenRepository extends JpaRepository<AuthRefreshTokenEntity, Long> {
    Optional<AuthRefreshTokenEntity> findByTokenHash(String tokenHash);

    long countByRevokedAtIsNullAndExpiresAtAfter(Instant now);

    @Modifying
    @Query("update AuthRefreshTokenEntity t set t.revokedAt = :revokedAt where t.userId = :userId and t.revokedAt is null")
    int revokeActiveByUserId(Long userId, Instant revokedAt);

    @Modifying
    @Query("delete from AuthRefreshTokenEntity t where t.expiresAt < :cutoff")
    int deleteByExpiresAtBefore(Instant cutoff);
}

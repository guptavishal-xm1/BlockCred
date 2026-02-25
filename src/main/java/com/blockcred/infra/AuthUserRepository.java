package com.blockcred.infra;

import com.blockcred.domain.AuthUserStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface AuthUserRepository extends JpaRepository<AuthUserEntity, Long> {
    Optional<AuthUserEntity> findByUsernameIgnoreCase(String username);

    Optional<AuthUserEntity> findByEmailIgnoreCase(String email);

    Optional<AuthUserEntity> findByUsernameIgnoreCaseOrEmailIgnoreCase(String username, String email);

    long countByLockedUntilAfter(Instant now);

    long countByStatus(AuthUserStatus status);
}

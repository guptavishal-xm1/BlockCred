package com.blockcred.infra;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

public interface AuditDigestRepository extends JpaRepository<AuditDigestEntity, Long> {
    Optional<AuditDigestEntity> findByDigestDate(LocalDate digestDate);
    Optional<AuditDigestEntity> findTopByOrderByDigestDateDesc();
    long deleteByCreatedAtBefore(Instant cutoff);
}

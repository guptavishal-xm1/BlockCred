package com.blockcred.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {
    List<AuditLogEntity> findByCredentialIdOrderByCreatedAtDesc(String credentialId, Pageable pageable);
    List<AuditLogEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
    boolean existsByCredentialIdAndActionAndCreatedAtAfter(String credentialId, String action, Instant cutoff);
    long countByActionStartingWithAndCreatedAtAfter(String prefix, Instant cutoff);
    long deleteByCreatedAtBefore(Instant cutoff);

    @Query("select a from AuditLogEntity a where (a.action like 'CONSISTENCY_%' or a.action like 'JOB_%FAILED') order by a.createdAt desc")
    List<AuditLogEntity> findAnomalies(Pageable pageable);

    @Query("select count(a) from AuditLogEntity a where a.createdAt >= :cutoff and (a.action like 'CONSISTENCY_%' or a.action like 'JOB_%FAILED')")
    long countAnomaliesAfter(@Param("cutoff") Instant cutoff);

    @Query("select a from AuditLogEntity a where a.createdAt >= :from and a.createdAt < :to order by a.createdAt asc")
    List<AuditLogEntity> findByCreatedAtRange(@Param("from") Instant from, @Param("to") Instant to);
}

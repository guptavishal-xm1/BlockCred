package com.blockcred.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {
    List<AuditLogEntity> findByCredentialIdOrderByCreatedAtDesc(String credentialId, Pageable pageable);
    List<AuditLogEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}

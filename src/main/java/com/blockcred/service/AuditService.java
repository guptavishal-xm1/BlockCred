package com.blockcred.service;

import com.blockcred.domain.AuditCategory;
import com.blockcred.domain.AuditSeverity;
import com.blockcred.infra.AuditLogEntity;
import com.blockcred.infra.AuditLogRepository;
import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class AuditService {
    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void log(String action, String credentialId, String actor, String details) {
        log(action, credentialId, actor, details, AuditSeverity.INFO, defaultCategory(action));
    }

    public void log(String action, String credentialId, String actor, String details, AuditSeverity severity, AuditCategory category) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setAction(action);
        entity.setCredentialId(credentialId);
        entity.setActor(actor);
        entity.setSeverity(severity);
        entity.setCategory(category);
        entity.setRequestId(MDC.get("requestId"));
        entity.setDetails(details);
        auditLogRepository.save(entity);
    }

    public List<AuditLogEntity> latestByCredential(String credentialId, int limit) {
        return auditLogRepository.findByCredentialIdOrderByCreatedAtDesc(credentialId, PageRequest.of(0, Math.max(1, limit)));
    }

    public List<AuditLogEntity> latest(int limit) {
        return auditLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, Math.max(1, limit)));
    }

    public List<AuditLogEntity> anomalies(int limit) {
        return auditLogRepository.findAnomalies(PageRequest.of(0, Math.max(1, limit)));
    }

    public boolean hasRecentAction(String credentialId, String action, Instant cutoff) {
        return auditLogRepository.existsByCredentialIdAndActionAndCreatedAtAfter(credentialId, action, cutoff);
    }

    public long countRecentConsistencyAnomalies(Instant cutoff) {
        return auditLogRepository.countByActionStartingWithAndCreatedAtAfter("CONSISTENCY_", cutoff);
    }

    public long countRecentAnomalies(Instant cutoff) {
        return auditLogRepository.countAnomaliesAfter(cutoff);
    }

    public long countByActionAfter(String action, Instant cutoff) {
        return auditLogRepository.countByActionAndCreatedAtAfter(action, cutoff);
    }

    public List<AuditLogEntity> byCreatedAtRange(Instant from, Instant to) {
        return auditLogRepository.findByCreatedAtRange(from, to);
    }

    public long deleteOlderThan(Instant cutoff) {
        return auditLogRepository.deleteByCreatedAtBefore(cutoff);
    }

    private AuditCategory defaultCategory(String action) {
        if (action == null) {
            return AuditCategory.SYSTEM;
        }
        if (action.startsWith("CONSISTENCY_")) {
            return AuditCategory.CONSISTENCY;
        }
        if (action.startsWith("WALLET_") || action.startsWith("AUTH_")) {
            return AuditCategory.SECURITY;
        }
        if (action.startsWith("RECONCILE_") || action.startsWith("JOB_")) {
            return AuditCategory.OPS;
        }
        return AuditCategory.CREDENTIAL;
    }
}

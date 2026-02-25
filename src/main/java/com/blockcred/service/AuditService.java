package com.blockcred.service;

import com.blockcred.infra.AuditLogEntity;
import com.blockcred.infra.AuditLogRepository;
import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditService {
    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void log(String action, String credentialId, String actor, String details) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setAction(action);
        entity.setCredentialId(credentialId);
        entity.setActor(actor);
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
}

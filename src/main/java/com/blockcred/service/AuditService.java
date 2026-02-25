package com.blockcred.service;

import com.blockcred.infra.AuditLogEntity;
import com.blockcred.infra.AuditLogRepository;
import org.springframework.stereotype.Service;

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
        entity.setDetails(details);
        auditLogRepository.save(entity);
    }
}

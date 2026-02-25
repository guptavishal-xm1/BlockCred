package com.blockcred.service;

import com.blockcred.api.OpsAuditEntryResponse;
import com.blockcred.api.OpsCredentialStateResponse;
import com.blockcred.api.OpsJobSummaryResponse;
import com.blockcred.domain.JobStatus;
import com.blockcred.infra.AnchorJobEntity;
import com.blockcred.infra.AnchorJobRepository;
import com.blockcred.infra.AuditLogEntity;
import com.blockcred.infra.CredentialEntity;
import com.blockcred.infra.CredentialRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OpsQueryService {
    private final CredentialRepository credentialRepository;
    private final AnchorJobRepository anchorJobRepository;
    private final AuditService auditService;

    public OpsQueryService(
            CredentialRepository credentialRepository,
            AnchorJobRepository anchorJobRepository,
            AuditService auditService
    ) {
        this.credentialRepository = credentialRepository;
        this.anchorJobRepository = anchorJobRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public OpsCredentialStateResponse credentialState(String credentialId) {
        CredentialEntity credential = credentialRepository.findByCredentialId(credentialId)
                .orElseThrow(() -> new IllegalArgumentException("Credential not found"));
        OpsJobSummaryResponse latestJob = anchorJobRepository.findTopByCredentialIdOrderByCreatedAtDesc(credentialId)
                .map(this::toJobSummary)
                .orElse(null);

        return new OpsCredentialStateResponse(
                credential.getCredentialId(),
                credential.getHash(),
                credential.getLifecycleStatus(),
                credential.getLastTxHash(),
                credential.getUpdatedAt(),
                latestJob
        );
    }

    @Transactional(readOnly = true)
    public List<OpsJobSummaryResponse> jobs(JobStatus status, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        List<AnchorJobEntity> jobs;
        if (status == null) {
            jobs = anchorJobRepository.findByStatusInOrderByUpdatedAtDesc(
                    List.of(JobStatus.PENDING, JobStatus.RETRYABLE, JobStatus.SENT, JobStatus.FINAL_FAILED),
                    PageRequest.of(0, safeLimit)
            );
        } else {
            jobs = anchorJobRepository.findByStatusOrderByUpdatedAtDesc(status, PageRequest.of(0, safeLimit));
        }
        return jobs.stream().map(this::toJobSummary).toList();
    }

    @Transactional(readOnly = true)
    public List<OpsAuditEntryResponse> audit(String credentialId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        List<AuditLogEntity> logs = credentialId == null || credentialId.isBlank()
                ? auditService.latest(safeLimit)
                : auditService.latestByCredential(credentialId, safeLimit);
        return logs.stream().map(this::toAuditEntry).toList();
    }

    private OpsJobSummaryResponse toJobSummary(AnchorJobEntity job) {
        return new OpsJobSummaryResponse(
                job.getId(),
                job.getCredentialId(),
                job.getHash(),
                job.getJobType(),
                job.getStatus(),
                job.getRetryCount(),
                job.getNextRunAt(),
                job.getLastAttemptAt(),
                job.getFailureCode(),
                job.getLastError(),
                job.getLastManualTriggerAt(),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }

    private OpsAuditEntryResponse toAuditEntry(AuditLogEntity log) {
        return new OpsAuditEntryResponse(
                log.getId(),
                log.getAction(),
                log.getCredentialId(),
                log.getActor(),
                log.getRequestId(),
                log.getDetails(),
                log.getCreatedAt()
        );
    }
}

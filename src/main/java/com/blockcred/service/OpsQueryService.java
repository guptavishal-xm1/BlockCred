package com.blockcred.service;

import com.blockcred.api.OpsAuditEntryResponse;
import com.blockcred.api.OpsAnomalyResponse;
import com.blockcred.api.OpsCredentialStateResponse;
import com.blockcred.api.OpsJobSummaryResponse;
import com.blockcred.api.OpsSecurityStatusResponse;
import com.blockcred.api.OpsSummaryResponse;
import com.blockcred.domain.ChainLookupResult;
import com.blockcred.domain.JobStatus;
import com.blockcred.domain.JobType;
import com.blockcred.infra.AnchorJobEntity;
import com.blockcred.infra.AnchorJobRepository;
import com.blockcred.infra.AuditLogEntity;
import com.blockcred.infra.CredentialEntity;
import com.blockcred.infra.CredentialRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class OpsQueryService {
    private final CredentialRepository credentialRepository;
    private final AnchorJobRepository anchorJobRepository;
    private final AuditService auditService;
    private final WorkerHeartbeatService workerHeartbeatService;
    private final BlockchainGateway blockchainGateway;
    private final AuditDigestService auditDigestService;
    private final AuthService authService;
    private final ApiAccessService apiAccessService;
    private final int pendingMaxAgeMinutes;
    private final int retryThreshold;
    private final int revocationGraceMinutes;

    public OpsQueryService(
            CredentialRepository credentialRepository,
            AnchorJobRepository anchorJobRepository,
            AuditService auditService,
            WorkerHeartbeatService workerHeartbeatService,
            BlockchainGateway blockchainGateway,
            AuditDigestService auditDigestService,
            AuthService authService,
            ApiAccessService apiAccessService,
            @Value("${blockcred.ops.pending-max-age-minutes:10}") int pendingMaxAgeMinutes,
            @Value("${blockcred.ops.retry-threshold:3}") int retryThreshold,
            @Value("${blockcred.ops.revocation-grace-minutes:15}") int revocationGraceMinutes
    ) {
        this.credentialRepository = credentialRepository;
        this.anchorJobRepository = anchorJobRepository;
        this.auditService = auditService;
        this.workerHeartbeatService = workerHeartbeatService;
        this.blockchainGateway = blockchainGateway;
        this.auditDigestService = auditDigestService;
        this.authService = authService;
        this.apiAccessService = apiAccessService;
        this.pendingMaxAgeMinutes = pendingMaxAgeMinutes;
        this.retryThreshold = retryThreshold;
        this.revocationGraceMinutes = revocationGraceMinutes;
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

    @Transactional(readOnly = true)
    public OpsSummaryResponse summary() {
        Instant now = Instant.now();
        long pendingCount = anchorJobRepository.countByStatus(JobStatus.PENDING);
        long retryableCount = anchorJobRepository.countByStatus(JobStatus.RETRYABLE);
        long finalFailedCount = anchorJobRepository.countByStatus(JobStatus.FINAL_FAILED);

        Long pendingAge = maxAgeMinutes(JobStatus.PENDING, now);
        Long retryableAge = maxAgeMinutes(JobStatus.RETRYABLE, now);
        Long finalFailedAge = maxAgeMinutes(JobStatus.FINAL_FAILED, now);
        Instant lastWorkerRun = workerHeartbeatService.lastRunAt();

        boolean chainReachable = chainReachable();
        long recentAnomalies = auditService.countRecentAnomalies(now.minus(Duration.ofHours(24)));
        var latestDigest = auditDigestService.latestDigest();

        boolean pendingAgeAlert = pendingAge != null && pendingAge > pendingMaxAgeMinutes;
        boolean retryThresholdAlert = anchorJobRepository.findByStatusOrderByUpdatedAtDesc(JobStatus.RETRYABLE, PageRequest.of(0, 200))
                .stream()
                .anyMatch(job -> job.getRetryCount() >= retryThreshold);
        boolean finalFailedAlert = finalFailedCount > 0;
        boolean revocationPropagationAlert = anchorJobRepository.countByJobTypeAndStatusInAndCreatedAtBefore(
                JobType.REVOKE,
                List.of(JobStatus.PENDING, JobStatus.RETRYABLE, JobStatus.SENT),
                now.minus(Duration.ofMinutes(revocationGraceMinutes))
        ) > 0;

        return new OpsSummaryResponse(
                pendingCount,
                retryableCount,
                finalFailedCount,
                pendingAge,
                retryableAge,
                finalFailedAge,
                lastWorkerRun,
                chainReachable,
                recentAnomalies,
                latestDigest == null ? null : latestDigest.getDigestDate(),
                latestDigest == null ? null : latestDigest.getRecordCount(),
                pendingAgeAlert,
                retryThresholdAlert,
                finalFailedAlert,
                revocationPropagationAlert
        );
    }

    @Transactional(readOnly = true)
    public List<OpsAnomalyResponse> anomalies(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return auditService.anomalies(safeLimit).stream()
                .map(this::toAnomaly)
                .toList();
    }

    @Transactional(readOnly = true)
    public OpsSecurityStatusResponse securityStatus() {
        Instant now = Instant.now();
        long failedLogins24h = auditService.countByActionAfter("AUTH_LOGIN_FAILED", now.minus(Duration.ofHours(24)));
        return new OpsSecurityStatusResponse(
                authService.lockedAccountCount(),
                authService.disabledAccountCount(),
                failedLogins24h,
                authService.activeRefreshSessions(),
                apiAccessService.isLegacyHeaderEnabled(),
                now
        );
    }

    private OpsJobSummaryResponse toJobSummary(AnchorJobEntity job) {
        return new OpsJobSummaryResponse(
                job.getId(),
                job.getCredentialId(),
                job.getHash(),
                job.getJobType(),
                job.getStatus(),
                statusLabel(job),
                recommendedAction(job),
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
            log.getSeverity(),
            log.getCategory(),
            log.getRequestId(),
            log.getDetails(),
            log.getCreatedAt()
        );
    }

    private OpsAnomalyResponse toAnomaly(AuditLogEntity log) {
        return new OpsAnomalyResponse(
                log.getId(),
                log.getAction(),
                log.getCredentialId(),
                log.getSeverity(),
                log.getCategory(),
                log.getDetails(),
                log.getCreatedAt(),
                recommendedActionForAnomaly(log.getAction())
        );
    }

    private String statusLabel(AnchorJobEntity job) {
        return switch (job.getStatus()) {
            case PENDING -> "Queued for blockchain submission";
            case SENT -> "Submission in progress";
            case RETRYABLE -> "Retry pending after transient failure";
            case CONFIRMED -> "Confirmed on blockchain";
            case FINAL_FAILED -> "Manual intervention required";
        };
    }

    private String recommendedAction(AnchorJobEntity job) {
        return switch (job.getStatus()) {
            case PENDING, SENT -> "Wait for worker cycle and re-check shortly.";
            case RETRYABLE -> "If retries stay high, run reconcile once and verify chain state.";
            case CONFIRMED -> "No action required.";
            case FINAL_FAILED -> "Use ops_state and reconcile after validating chain and wallet status.";
        };
    }

    private String recommendedActionForAnomaly(String action) {
        if (action == null) {
            return "Inspect latest ops summary and run credential-level state check.";
        }
        if (action.startsWith("CONSISTENCY_")) {
            return "Run reconcile for this credential and verify local vs chain status.";
        }
        if (action.startsWith("JOB_")) {
            return "Inspect failure code and retry count, then reconcile if actionable.";
        }
        return "Inspect ops audit timeline for this credential.";
    }

    private Long maxAgeMinutes(JobStatus status, Instant now) {
        return anchorJobRepository.findTopByStatusOrderByCreatedAtAsc(status)
                .map(job -> Duration.between(job.getCreatedAt(), now).toMinutes())
                .orElse(null);
    }

    private boolean chainReachable() {
        try {
            ChainLookupResult lookup = blockchainGateway.lookup("__ops_healthcheck__");
            return lookup.chainReachable();
        } catch (Exception e) {
            return false;
        }
    }
}

package com.blockcred.service;

import com.blockcred.domain.*;
import com.blockcred.infra.AnchorJobEntity;
import com.blockcred.infra.AnchorJobRepository;
import com.blockcred.infra.CredentialEntity;
import com.blockcred.infra.CredentialRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class JobService {
    private static final Logger log = LoggerFactory.getLogger(JobService.class);
    private static final int MAX_RETRIES = 5;
    private static final List<JobStatus> DUE_STATUSES = List.of(JobStatus.PENDING, JobStatus.RETRYABLE);

    private final AnchorJobRepository jobRepository;
    private final CredentialRepository credentialRepository;
    private final BlockchainGateway blockchainGateway;
    private final CredentialService credentialService;
    private final AuditService auditService;
    private final int dueBatchSize;
    private final int maxBatchesPerTick;
    private final long staleSentSeconds;
    private final Counter jobsProcessedCounter;
    private final Counter jobsFailedCounter;
    private final Counter reconcileInvocationsCounter;
    private final Timer chainLookupTimer;
    private final Timer jobProcessingTimer;

    public JobService(
            AnchorJobRepository jobRepository,
            CredentialRepository credentialRepository,
            BlockchainGateway blockchainGateway,
            CredentialService credentialService,
            AuditService auditService,
            MeterRegistry meterRegistry,
            @Value("${blockcred.worker.due-batch-size:100}") int dueBatchSize,
            @Value("${blockcred.worker.max-batches-per-tick:5}") int maxBatchesPerTick,
            @Value("${blockcred.worker.stale-sent-seconds:120}") long staleSentSeconds
    ) {
        this.jobRepository = jobRepository;
        this.credentialRepository = credentialRepository;
        this.blockchainGateway = blockchainGateway;
        this.credentialService = credentialService;
        this.auditService = auditService;
        this.dueBatchSize = dueBatchSize;
        this.maxBatchesPerTick = maxBatchesPerTick;
        this.staleSentSeconds = staleSentSeconds;
        this.jobsProcessedCounter = meterRegistry.counter("blockcred.jobs.processed");
        this.jobsFailedCounter = meterRegistry.counter("blockcred.jobs.failed");
        this.reconcileInvocationsCounter = meterRegistry.counter("blockcred.reconcile.invocations");
        this.chainLookupTimer = meterRegistry.timer("blockcred.chain.lookup.latency");
        this.jobProcessingTimer = meterRegistry.timer("blockcred.jobs.process.latency");

        Gauge.builder("blockcred.jobs.pending", jobRepository, repo -> repo.countByStatus(JobStatus.PENDING)).register(meterRegistry);
        Gauge.builder("blockcred.jobs.retryable", jobRepository, repo -> repo.countByStatus(JobStatus.RETRYABLE)).register(meterRegistry);
        Gauge.builder("blockcred.jobs.final_failed", jobRepository, repo -> repo.countByStatus(JobStatus.FINAL_FAILED)).register(meterRegistry);
    }

    public void processDueJobs() {
        recoverStaleSentJobs();

        for (int batch = 0; batch < maxBatchesPerTick; batch++) {
            List<AnchorJobEntity> due = jobRepository.findByStatusInAndNextRunAtBeforeOrderByNextRunAtAsc(
                    DUE_STATUSES,
                    Instant.now().plusSeconds(1),
                    PageRequest.of(0, dueBatchSize)
            );
            if (due.isEmpty()) {
                return;
            }

            for (AnchorJobEntity candidate : due) {
                if (tryClaim(candidate)) {
                    process(candidate);
                }
            }
        }
    }

    public void process(AnchorJobEntity job) {
        Timer.Sample sample = Timer.start();
        Instant startedAt = Instant.now();

        try {
            try {
                if (job.getJobType() == JobType.ANCHOR) {
                    credentialService.queueAnchorRetry(job.getCredentialId());
                }
                ChainLookupResult chainStatus = safeLookup(job.getHash());
                if (job.getJobType() == JobType.ANCHOR && chainStatus.chainRecordFound()) {
                    credentialService.markAnchored(job.getCredentialId(), chainStatus.txHash());
                    job.setStatus(JobStatus.CONFIRMED);
                    job.setFailureCode(null);
                    job.setLastError(null);
                    auditService.log("ANCHOR_SYNCED", job.getCredentialId(), "worker", "Already anchored on-chain");
                    jobsProcessedCounter.increment();
                    log.info("event=job_synced requestId={} jobId={} credentialId={} jobType={} status={} txHash={}",
                            requestId(), job.getId(), job.getCredentialId(), job.getJobType(), job.getStatus(), chainStatus.txHash());
                    jobRepository.save(job);
                    return;
                }
                if (job.getJobType() == JobType.REVOKE && chainStatus.chainRecordFound() && chainStatus.chainRevoked()) {
                    credentialService.markRevoked(job.getCredentialId(), chainStatus.txHash());
                    job.setStatus(JobStatus.CONFIRMED);
                    job.setFailureCode(null);
                    job.setLastError(null);
                    auditService.log("REVOKE_SYNCED", job.getCredentialId(), "worker", "Already revoked on-chain");
                    jobsProcessedCounter.increment();
                    log.info("event=job_synced requestId={} jobId={} credentialId={} jobType={} status={} txHash={}",
                            requestId(), job.getId(), job.getCredentialId(), job.getJobType(), job.getStatus(), chainStatus.txHash());
                    jobRepository.save(job);
                    return;
                }

                String txHash = submit(job);
                if (job.getJobType() == JobType.ANCHOR) {
                    credentialService.markAnchored(job.getCredentialId(), txHash);
                    auditService.log("ANCHORED", job.getCredentialId(), "worker", txHash);
                } else {
                    credentialService.markRevoked(job.getCredentialId(), txHash);
                    auditService.log("REVOKED", job.getCredentialId(), "worker", txHash);
                }
                job.setStatus(JobStatus.CONFIRMED);
                job.setFailureCode(null);
                job.setLastError(null);
                jobsProcessedCounter.increment();
                log.info("event=job_confirmed requestId={} jobId={} credentialId={} jobType={} status={} txHash={}",
                        requestId(), job.getId(), job.getCredentialId(), job.getJobType(), job.getStatus(), txHash);
            } catch (Exception e) {
                job.setRetryCount(job.getRetryCount() + 1);
                job.setLastError(e.getMessage());
                job.setFailureCode(classifyFailure(e));
                if (job.getJobType() == JobType.ANCHOR) {
                    try {
                        credentialService.markAnchorFailed(job.getCredentialId());
                    } catch (Exception stateTransitionException) {
                        log.warn("event=job_mark_anchor_failed_error requestId={} jobId={} credentialId={} message={}",
                                requestId(), job.getId(), job.getCredentialId(), stateTransitionException.getMessage());
                    }
                }
                if (job.getRetryCount() >= MAX_RETRIES) {
                    job.setStatus(JobStatus.FINAL_FAILED);
                } else {
                    job.setStatus(JobStatus.RETRYABLE);
                    job.setNextRunAt(startedAt.plus(Duration.ofSeconds(15L * job.getRetryCount())));
                }
                auditService.log("JOB_FAILED", job.getCredentialId(), "worker", e.getMessage());
                jobsFailedCounter.increment();
                log.warn("event=job_failed requestId={} jobId={} credentialId={} jobType={} retryCount={} status={} failureCode={} message={}",
                        requestId(), job.getId(), job.getCredentialId(), job.getJobType(), job.getRetryCount(), job.getStatus(), job.getFailureCode(), e.getMessage());
            }
            jobRepository.save(job);
        } finally {
            sample.stop(jobProcessingTimer);
        }
    }

    @Transactional
    public ReconcileDecision reconcile(String credentialId, String actor, Duration cooldown) {
        reconcileInvocationsCounter.increment();
        CredentialEntity credential = credentialRepository.findByCredentialId(credentialId)
                .orElseThrow(() -> new IllegalArgumentException("Credential not found"));

        AnchorJobEntity job = jobRepository.findTopByCredentialIdOrderByCreatedAtDesc(credentialId).orElse(null);
        Instant checkedAt = Instant.now();
        if (job == null) {
            auditService.log("RECONCILE_REJECTED", credentialId, actor, "No job found");
            return new ReconcileDecision(
                    ReconcileResultCode.NO_ACTIONABLE_JOB,
                    credentialId,
                    null,
                    null,
                    checkedAt,
                    "No actionable job found for credential"
            );
        }

        if (job.getStatus() == JobStatus.CONFIRMED) {
            auditService.log("RECONCILE_REJECTED", credentialId, actor, "Already confirmed");
            return decision(ReconcileResultCode.NO_ACTIONABLE_JOB, job, checkedAt, "No actionable job: already confirmed");
        }

        if (job.getLastManualTriggerAt() != null && job.getLastManualTriggerAt().plus(cooldown).isAfter(Instant.now())) {
            auditService.log("RECONCILE_REJECTED", credentialId, actor, "Cooldown active");
            return decision(ReconcileResultCode.COOLDOWN_ACTIVE, job, checkedAt, "Manual reconcile cooldown active");
        }

        ChainLookupResult chain = safeLookup(job.getHash());
        if (job.getJobType() == JobType.ANCHOR && chain.chainRecordFound()) {
            credentialService.markAnchored(credentialId, chain.txHash());
            job.setStatus(JobStatus.CONFIRMED);
            job.setFailureCode(null);
            job.setLastError(null);
            job.setLastManualTriggerAt(Instant.now());
            jobRepository.save(job);
            auditService.log("RECONCILE_SYNC_ANCHOR", credentialId, actor, "Synced from chain");
            credentialService.evictVerificationCacheForHash(job.getHash());
            log.info("event=reconcile_synced requestId={} credentialId={} jobId={} jobType={} status={} txHash={}",
                    requestId(), credentialId, job.getId(), job.getJobType(), job.getStatus(), chain.txHash());
            return decision(ReconcileResultCode.SYNCED, job, checkedAt, "Synced from chain state");
        }

        if (job.getJobType() == JobType.REVOKE && chain.chainRecordFound() && chain.chainRevoked()) {
            credentialService.markRevoked(credentialId, chain.txHash());
            job.setStatus(JobStatus.CONFIRMED);
            job.setFailureCode(null);
            job.setLastError(null);
            job.setLastManualTriggerAt(Instant.now());
            jobRepository.save(job);
            auditService.log("RECONCILE_SYNC_REVOKE", credentialId, actor, "Synced from chain");
            credentialService.evictVerificationCacheForHash(job.getHash());
            log.info("event=reconcile_synced requestId={} credentialId={} jobId={} jobType={} status={} txHash={}",
                    requestId(), credentialId, job.getId(), job.getJobType(), job.getStatus(), chain.txHash());
            return decision(ReconcileResultCode.SYNCED, job, checkedAt, "Synced from chain state");
        }

        job.setStatus(JobStatus.PENDING);
        job.setNextRunAt(Instant.now());
        job.setLastManualTriggerAt(Instant.now());
        if (job.getJobType() == JobType.ANCHOR) {
            credentialService.queueAnchorRetry(credentialId);
        }
        jobRepository.save(job);
        auditService.log("RECONCILE_RETRY", credentialId, actor, credential.getHash());
        credentialService.evictVerificationCacheForHash(job.getHash());
        log.info("event=reconcile_queued requestId={} credentialId={} jobId={} jobType={} status={}",
                requestId(), credentialId, job.getId(), job.getJobType(), job.getStatus());
        return decision(ReconcileResultCode.QUEUED, job, checkedAt, "Retry queued");
    }

    @Transactional
    public int recoverStaleSentJobs() {
        List<AnchorJobEntity> stale = jobRepository.findByStatusAndLastAttemptAtBefore(
                JobStatus.SENT,
                Instant.now().minusSeconds(staleSentSeconds),
                PageRequest.of(0, dueBatchSize)
        );
        for (AnchorJobEntity job : stale) {
            job.setStatus(JobStatus.RETRYABLE);
            job.setNextRunAt(Instant.now());
            job.setFailureCode(JobFailureCode.UNKNOWN);
            job.setLastError("Recovered stale SENT job");
            auditService.log("JOB_RECOVERED_STALE_SENT", job.getCredentialId(), "worker", "Recovered stale SENT job");
            log.warn("event=job_recovered_stale_sent requestId={} jobId={} credentialId={} jobType={} retryCount={}",
                    requestId(), job.getId(), job.getCredentialId(), job.getJobType(), job.getRetryCount());
            jobRepository.save(job);
        }
        return stale.size();
    }

    private boolean tryClaim(AnchorJobEntity candidate) {
        Instant attemptAt = Instant.now();
        int claimed = jobRepository.claimJob(candidate.getId(), candidate.getStatus(), JobStatus.SENT, attemptAt);
        if (claimed == 1) {
            candidate.setStatus(JobStatus.SENT);
            candidate.setLastAttemptAt(attemptAt);
            return true;
        }
        return false;
    }

    private String submit(AnchorJobEntity job) {
        return job.getJobType() == JobType.ANCHOR
                ? blockchainGateway.anchor(job.getHash())
                : blockchainGateway.revoke(job.getHash());
    }

    private ChainLookupResult safeLookup(String hash) {
        Timer.Sample sample = Timer.start();
        try {
            return blockchainGateway.lookup(hash);
        } catch (ChainUnavailableException e) {
            return ChainLookupResult.unavailable();
        } finally {
            sample.stop(chainLookupTimer);
        }
    }

    private ReconcileDecision decision(ReconcileResultCode result, AnchorJobEntity job, Instant checkedAt, String message) {
        return new ReconcileDecision(
                result,
                job.getCredentialId(),
                job.getJobType(),
                job.getStatus(),
                checkedAt,
                message
        );
    }

    private JobFailureCode classifyFailure(Exception e) {
        if (e instanceof ChainUnavailableException) {
            return JobFailureCode.CHAIN_UNAVAILABLE;
        }
        String message = e.getMessage() == null ? "" : e.getMessage();
        if (message.contains("Already anchored")
                || message.contains("Already revoked")
                || message.contains("Credential not anchored")) {
            return JobFailureCode.CHAIN_REJECTED;
        }
        if (message.contains("Illegal transition")) {
            return JobFailureCode.LOCAL_TRANSITION_ERROR;
        }
        return JobFailureCode.UNKNOWN;
    }

    private String requestId() {
        String requestId = MDC.get("requestId");
        return requestId == null ? "n/a" : requestId;
    }
}

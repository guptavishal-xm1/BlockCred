package com.blockcred.service;

import com.blockcred.domain.*;
import com.blockcred.infra.AnchorJobEntity;
import com.blockcred.infra.AnchorJobRepository;
import com.blockcred.infra.CredentialEntity;
import com.blockcred.infra.CredentialRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class JobService {
    private static final int MAX_RETRIES = 5;

    private final AnchorJobRepository jobRepository;
    private final CredentialRepository credentialRepository;
    private final BlockchainGateway blockchainGateway;
    private final CredentialService credentialService;
    private final AuditService auditService;

    public JobService(
            AnchorJobRepository jobRepository,
            CredentialRepository credentialRepository,
            BlockchainGateway blockchainGateway,
            CredentialService credentialService,
            AuditService auditService
    ) {
        this.jobRepository = jobRepository;
        this.credentialRepository = credentialRepository;
        this.blockchainGateway = blockchainGateway;
        this.credentialService = credentialService;
        this.auditService = auditService;
    }

    public void processDueJobs() {
        List<AnchorJobEntity> due = jobRepository.findByStatusInAndNextRunAtBefore(
                List.of(JobStatus.PENDING, JobStatus.RETRYABLE), Instant.now().plusSeconds(1));

        for (AnchorJobEntity job : due) {
            process(job);
        }
    }

    public void process(AnchorJobEntity job) {
        job.setStatus(JobStatus.SENT);
        job.setLastAttemptAt(Instant.now());

        try {
            ChainLookupResult chainStatus = safeLookup(job.getHash());
            if (job.getJobType() == JobType.ANCHOR && chainStatus.chainRecordFound()) {
                credentialService.markAnchored(job.getCredentialId(), chainStatus.txHash());
                job.setStatus(JobStatus.CONFIRMED);
                auditService.log("ANCHOR_SYNCED", job.getCredentialId(), "worker", "Already anchored on-chain");
                jobRepository.save(job);
                return;
            }
            if (job.getJobType() == JobType.REVOKE && chainStatus.chainRecordFound() && chainStatus.chainRevoked()) {
                credentialService.markRevoked(job.getCredentialId(), chainStatus.txHash());
                job.setStatus(JobStatus.CONFIRMED);
                auditService.log("REVOKE_SYNCED", job.getCredentialId(), "worker", "Already revoked on-chain");
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
            job.setLastError(null);
        } catch (Exception e) {
            job.setRetryCount(job.getRetryCount() + 1);
            job.setLastError(e.getMessage());
            if (job.getJobType() == JobType.ANCHOR) {
                credentialService.markAnchorFailed(job.getCredentialId());
            }
            if (job.getRetryCount() >= MAX_RETRIES) {
                job.setStatus(JobStatus.FINAL_FAILED);
            } else {
                job.setStatus(JobStatus.RETRYABLE);
                job.setNextRunAt(Instant.now().plus(Duration.ofSeconds(15L * job.getRetryCount())));
            }
            auditService.log("JOB_FAILED", job.getCredentialId(), "worker", e.getMessage());
        }
        jobRepository.save(job);
    }

    @Transactional
    public String reconcile(String credentialId, String actor, Duration cooldown) {
        CredentialEntity credential = credentialRepository.findByCredentialId(credentialId)
                .orElseThrow(() -> new IllegalArgumentException("Credential not found"));

        AnchorJobEntity job = jobRepository.findTopByCredentialIdOrderByCreatedAtDesc(credentialId)
                .orElseThrow(() -> new IllegalStateException("No job found for credential"));

        if (job.getStatus() == JobStatus.CONFIRMED) {
            throw new IllegalStateException("No actionable job: already confirmed");
        }

        if (job.getLastManualTriggerAt() != null && job.getLastManualTriggerAt().plus(cooldown).isAfter(Instant.now())) {
            throw new IllegalStateException("Manual reconcile cooldown active");
        }

        ChainLookupResult chain = safeLookup(job.getHash());
        if (job.getJobType() == JobType.ANCHOR && chain.chainRecordFound()) {
            credentialService.markAnchored(credentialId, chain.txHash());
            job.setStatus(JobStatus.CONFIRMED);
            job.setLastManualTriggerAt(Instant.now());
            auditService.log("RECONCILE_SYNC_ANCHOR", credentialId, actor, "Synced from chain");
            return "synced";
        }

        if (job.getJobType() == JobType.REVOKE && chain.chainRecordFound() && chain.chainRevoked()) {
            credentialService.markRevoked(credentialId, chain.txHash());
            job.setStatus(JobStatus.CONFIRMED);
            job.setLastManualTriggerAt(Instant.now());
            auditService.log("RECONCILE_SYNC_REVOKE", credentialId, actor, "Synced from chain");
            return "synced";
        }

        job.setStatus(JobStatus.PENDING);
        job.setNextRunAt(Instant.now());
        job.setLastManualTriggerAt(Instant.now());
        auditService.log("RECONCILE_RETRY", credentialId, actor, credential.getHash());
        return "queued";
    }

    private String submit(AnchorJobEntity job) {
        return job.getJobType() == JobType.ANCHOR
                ? blockchainGateway.anchor(job.getHash())
                : blockchainGateway.revoke(job.getHash());
    }

    private ChainLookupResult safeLookup(String hash) {
        try {
            return blockchainGateway.lookup(hash);
        } catch (ChainUnavailableException e) {
            return ChainLookupResult.unavailable();
        }
    }
}

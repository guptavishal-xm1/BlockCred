package com.blockcred.service;

import com.blockcred.api.CredentialResponse;
import com.blockcred.domain.*;
import com.blockcred.infra.AnchorJobEntity;
import com.blockcred.infra.AnchorJobRepository;
import com.blockcred.infra.CredentialEntity;
import com.blockcred.infra.CredentialRepository;
import jakarta.transaction.Transactional;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class CredentialService {
    private static final List<JobStatus> ACTIVE_STATUSES = List.of(JobStatus.PENDING, JobStatus.RETRYABLE, JobStatus.SENT);

    private final CredentialRepository credentialRepository;
    private final AnchorJobRepository anchorJobRepository;
    private final CredentialHashService hashService;
    private final CredentialStateMachine stateMachine;
    private final CacheManager cacheManager;

    public CredentialService(
            CredentialRepository credentialRepository,
            AnchorJobRepository anchorJobRepository,
            CredentialHashService hashService,
            CredentialStateMachine stateMachine,
            CacheManager cacheManager
    ) {
        this.credentialRepository = credentialRepository;
        this.anchorJobRepository = anchorJobRepository;
        this.hashService = hashService;
        this.stateMachine = stateMachine;
        this.cacheManager = cacheManager;
    }

    @Transactional
    public CredentialResponse createAndQueueAnchor(CredentialCanonicalPayload payload) {
        String hash = hashService.generateHash(payload);
        String canonical = hashService.canonicalJson(payload);

        CredentialEntity credential = credentialRepository.findByCredentialId(payload.credentialId()).orElseGet(CredentialEntity::new);
        if (credential.getId() != null) {
            throw new IllegalStateException("Credential already exists");
        }

        credential.setCredentialId(payload.credentialId());
        credential.setHash(hash);
        credential.setCanonicalPayloadJson(canonical);
        credential.setLifecycleStatus(CredentialLifecycleStatus.DRAFT);
        credential.setLifecycleStatus(stateMachine.transition(credential.getLifecycleStatus(), CredentialEvent.APPROVE));
        credential.setLifecycleStatus(stateMachine.transition(credential.getLifecycleStatus(), CredentialEvent.ISSUE));
        credential.setLifecycleStatus(stateMachine.transition(credential.getLifecycleStatus(), CredentialEvent.ANCHOR_QUEUED));
        credentialRepository.save(credential);

        ensureActiveJob(payload.credentialId(), hash, JobType.ANCHOR);
        evict(hash);
        return new CredentialResponse(payload.credentialId(), hash, credential.getLifecycleStatus());
    }

    @Transactional
    public CredentialResponse requestRevoke(String credentialId) {
        CredentialEntity credential = credentialRepository.findByCredentialId(credentialId)
                .orElseThrow(() -> new IllegalArgumentException("Credential not found"));

        credential.setLifecycleStatus(stateMachine.transition(credential.getLifecycleStatus(), CredentialEvent.REVOKE_REQUESTED));
        credentialRepository.save(credential);
        ensureActiveJob(credentialId, credential.getHash(), JobType.REVOKE);
        evict(credential.getHash());
        return new CredentialResponse(credentialId, credential.getHash(), credential.getLifecycleStatus());
    }

    @Transactional
    public void markAnchored(String credentialId, String txHash) {
        CredentialEntity credential = credentialRepository.findByCredentialId(credentialId)
                .orElseThrow(() -> new IllegalArgumentException("Credential not found"));
        credential.setLifecycleStatus(stateMachine.transition(credential.getLifecycleStatus(), CredentialEvent.ANCHOR_CONFIRMED));
        credential.setLastTxHash(txHash);
        credentialRepository.save(credential);
        evict(credential.getHash());
    }

    @Transactional
    public void markAnchorFailed(String credentialId) {
        CredentialEntity credential = credentialRepository.findByCredentialId(credentialId)
                .orElseThrow(() -> new IllegalArgumentException("Credential not found"));
        credential.setLifecycleStatus(stateMachine.transition(credential.getLifecycleStatus(), CredentialEvent.ANCHOR_FAILED));
        credentialRepository.save(credential);
        evict(credential.getHash());
    }

    @Transactional
    public void markRevoked(String credentialId, String txHash) {
        CredentialEntity credential = credentialRepository.findByCredentialId(credentialId)
                .orElseThrow(() -> new IllegalArgumentException("Credential not found"));
        credential.setLifecycleStatus(stateMachine.transition(credential.getLifecycleStatus(), CredentialEvent.REVOKE_CONFIRMED));
        credential.setLastTxHash(txHash);
        credentialRepository.save(credential);
        evict(credential.getHash());
    }

    public Optional<String> findCredentialHashByCredentialId(String credentialId) {
        return credentialRepository.findByCredentialId(credentialId).map(CredentialEntity::getHash);
    }

    private void ensureActiveJob(String credentialId, String hash, JobType type) {
        boolean exists = anchorJobRepository.existsByCredentialIdAndJobTypeAndStatusIn(credentialId, type, ACTIVE_STATUSES);
        if (!exists) {
            AnchorJobEntity job = new AnchorJobEntity();
            job.setCredentialId(credentialId);
            job.setHash(hash);
            job.setJobType(type);
            job.setStatus(JobStatus.PENDING);
            job.setRetryCount(0);
            job.setNextRunAt(Instant.now());
            anchorJobRepository.save(job);
        }
    }

    private void evict(String hash) {
        if (cacheManager.getCache("verificationByHash") != null) {
            cacheManager.getCache("verificationByHash").evict(hash);
        }
    }
}

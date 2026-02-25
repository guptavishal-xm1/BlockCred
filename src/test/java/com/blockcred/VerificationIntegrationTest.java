package com.blockcred;

import com.blockcred.api.CredentialResponse;
import com.blockcred.api.VerificationResponse;
import com.blockcred.domain.CredentialCanonicalPayload;
import com.blockcred.domain.ReconcileResultCode;
import com.blockcred.domain.VerificationStatus;
import com.blockcred.domain.ReconcileDecision;
import com.blockcred.infra.AnchorJobRepository;
import com.blockcred.infra.AuditLogRepository;
import com.blockcred.infra.CredentialRepository;
import com.blockcred.service.CredentialService;
import com.blockcred.service.InMemoryBlockchainGateway;
import com.blockcred.service.JobService;
import com.blockcred.service.VerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
class VerificationIntegrationTest {
    @Autowired
    private CredentialService credentialService;
    @Autowired
    private JobService jobService;
    @Autowired
    private VerificationService verificationService;
    @Autowired
    private InMemoryBlockchainGateway blockchainGateway;
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private CredentialRepository credentialRepository;
    @Autowired
    private AnchorJobRepository anchorJobRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;

    private CredentialCanonicalPayload payload;

    @BeforeEach
    void setup() {
        auditLogRepository.deleteAll();
        anchorJobRepository.deleteAll();
        credentialRepository.deleteAll();
        blockchainGateway.clear();
        cacheManager.getCache("verificationByHash").clear();

        payload = new CredentialCanonicalPayload(
                "CRED-INT-001", "UNI-001", "STU-001", "Computer Science", "B.Tech",
                LocalDate.of(2026, 2, 25), "550e8400-e29b-41d4-a716-446655440111", "1.0");
        blockchainGateway.setUnavailable(false);
    }

    @Test
    void shouldInvalidateCacheAfterRevoke() {
        CredentialResponse issued = credentialService.createAndQueueAnchor(payload);
        jobService.processDueJobs();

        VerificationResponse first = verificationService.verifyHash(issued.hash());
        assertNotNull(cacheManager.getCache("verificationByHash").get(issued.hash()));
        assertEquals(VerificationStatus.VALID, first.verificationStatus());

        credentialService.requestRevoke(payload.credentialId());
        assertNull(cacheManager.getCache("verificationByHash").get(issued.hash()));

        jobService.processDueJobs();
        VerificationResponse revoked = verificationService.verifyHash(issued.hash());
        assertEquals(VerificationStatus.REVOKED, revoked.verificationStatus());
    }

    @Test
    void shouldRecoverFromFailedAnchorViaReconcileAndRetry() {
        CredentialResponse issued = credentialService.createAndQueueAnchor(payload);

        blockchainGateway.setUnavailable(true);
        jobService.processDueJobs();

        VerificationResponse pending = verificationService.verifyHash(issued.hash());
        assertEquals(VerificationStatus.PENDING_ANCHOR, pending.verificationStatus());

        blockchainGateway.setUnavailable(false);
        ReconcileDecision result = jobService.reconcile(payload.credentialId(), "admin", Duration.ZERO);
        assertTrue(result.result() == ReconcileResultCode.QUEUED || result.result() == ReconcileResultCode.SYNCED);

        jobService.processDueJobs();
        VerificationResponse valid = verificationService.verifyHash(issued.hash());
        assertEquals(VerificationStatus.VALID, valid.verificationStatus());
    }

    @Test
    void shouldReturnChainUnavailableWhenAlreadyAnchoredAndRpcDown() {
        CredentialResponse issued = credentialService.createAndQueueAnchor(payload);
        jobService.processDueJobs();

        blockchainGateway.setUnavailable(true);
        VerificationResponse response = verificationService.verifyHash(issued.hash());
        assertEquals(VerificationStatus.CHAIN_UNAVAILABLE, response.verificationStatus());
    }

    @Test
    void shouldRecoverStaleSentJobToRetryable() {
        credentialService.createAndQueueAnchor(payload);
        var job = anchorJobRepository.findTopByCredentialIdOrderByCreatedAtDesc(payload.credentialId()).orElseThrow();
        job.setStatus(com.blockcred.domain.JobStatus.SENT);
        job.setLastAttemptAt(Instant.now().minusSeconds(300));
        anchorJobRepository.save(job);

        int recovered = jobService.recoverStaleSentJobs();
        assertEquals(1, recovered);

        var updated = anchorJobRepository.findTopByCredentialIdOrderByCreatedAtDesc(payload.credentialId()).orElseThrow();
        assertEquals(com.blockcred.domain.JobStatus.RETRYABLE, updated.getStatus());
        assertEquals("Recovered stale SENT job", updated.getLastError());
    }
}

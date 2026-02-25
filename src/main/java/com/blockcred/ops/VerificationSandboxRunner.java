package com.blockcred.ops;

import com.blockcred.domain.ChainLookupResult;
import com.blockcred.domain.CredentialCanonicalPayload;
import com.blockcred.domain.VerificationInput;
import com.blockcred.domain.VerificationVerdict;
import com.blockcred.infra.AnchorJobEntity;
import com.blockcred.infra.AnchorJobRepository;
import com.blockcred.infra.CredentialEntity;
import com.blockcred.infra.CredentialRepository;
import com.blockcred.service.BlockchainGateway;
import com.blockcred.service.CredentialHashService;
import com.blockcred.service.VerificationVerdictResolver;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@ConditionalOnProperty(name = "blockcred.sandbox.enabled", havingValue = "true")
public class VerificationSandboxRunner implements CommandLineRunner {
    private final CredentialHashService hashService;
    private final CredentialRepository credentialRepository;
    private final AnchorJobRepository anchorJobRepository;
    private final BlockchainGateway blockchainGateway;
    private final VerificationVerdictResolver resolver;

    public VerificationSandboxRunner(
            CredentialHashService hashService,
            CredentialRepository credentialRepository,
            AnchorJobRepository anchorJobRepository,
            BlockchainGateway blockchainGateway,
            VerificationVerdictResolver resolver
    ) {
        this.hashService = hashService;
        this.credentialRepository = credentialRepository;
        this.anchorJobRepository = anchorJobRepository;
        this.blockchainGateway = blockchainGateway;
        this.resolver = resolver;
    }

    @Override
    public void run(String... args) {
        CredentialCanonicalPayload sample = new CredentialCanonicalPayload(
                "CRED-SANDBOX", "UNI-001", "STU-001", "Computer Science", "B.Tech",
                LocalDate.of(2026, 2, 25), "550e8400-e29b-41d4-a716-446655440000", "1.0");

        String canonical = hashService.canonicalJson(sample);
        String hash = hashService.generateHash(sample);
        CredentialEntity entity = credentialRepository.findByHash(hash).orElse(null);
        ChainLookupResult chain;
        try {
            chain = blockchainGateway.lookup(hash);
        } catch (Exception e) {
            chain = ChainLookupResult.unavailable();
        }

        VerificationInput input = new VerificationInput(
                true,
                entity != null,
                entity != null ? entity.getLifecycleStatus() : null,
                chain.chainReachable(),
                chain.chainRecordFound(),
                chain.chainRevoked()
        );
        VerificationVerdict verdict = resolver.resolve(input);
        String runId = "sandbox-" + hash.substring(0, Math.min(12, hash.length()));
        AnchorJobEntity latestJob = anchorJobRepository.findTopByCredentialIdOrderByCreatedAtDesc(sample.credentialId()).orElse(null);

        System.out.println("=== Verification Sandbox ===");
        System.out.println("Run ID: " + runId);
        System.out.println("Canonical JSON: " + canonical);
        System.out.println("Hash: " + hash);
        System.out.println("DB Record Found: " + (entity != null));
        System.out.println("Chain Reachable: " + chain.chainReachable());
        System.out.println("Chain Record Found: " + chain.chainRecordFound());
        System.out.println("Verdict: " + verdict.status());
        System.out.println("Explanation: " + verdict.explanation());
        if (latestJob != null) {
            System.out.println("Latest Job ID: " + latestJob.getId());
            System.out.println("Latest Job Type: " + latestJob.getJobType());
            System.out.println("Latest Job Status: " + latestJob.getStatus());
            System.out.println("Latest Job Retry Count: " + latestJob.getRetryCount());
            System.out.println("Latest Job Failure Code: " + latestJob.getFailureCode());
            System.out.println("Latest Job Last Error: " + latestJob.getLastError());
            System.out.println("Latest Job Next Run At: " + latestJob.getNextRunAt());
        } else {
            System.out.println("Latest Job: none");
        }
    }
}

package com.blockcred.service;

import com.blockcred.api.VerificationResponse;
import com.blockcred.domain.*;
import com.blockcred.infra.CredentialEntity;
import com.blockcred.infra.CredentialRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.*;

@Service
public class VerificationService {
    private final CredentialHashService hashService;
    private final CredentialRepository credentialRepository;
    private final BlockchainGateway blockchainGateway;
    private final VerificationVerdictResolver resolver;
    private final Cache verificationCache;
    private final MeterRegistry meterRegistry;
    private final Timer chainLookupTimer;
    private final ExecutorService chainLookupExecutor;
    private final long chainLookupTimeoutMs;

    public VerificationService(
            CredentialHashService hashService,
            CredentialRepository credentialRepository,
            BlockchainGateway blockchainGateway,
            VerificationVerdictResolver resolver,
            CacheManager cacheManager,
            MeterRegistry meterRegistry,
            ExecutorService chainLookupExecutor,
            @Value("${blockcred.verify.chain-lookup-timeout-ms:800}") long chainLookupTimeoutMs
    ) {
        this.hashService = hashService;
        this.credentialRepository = credentialRepository;
        this.blockchainGateway = blockchainGateway;
        this.resolver = resolver;
        this.verificationCache = cacheManager.getCache("verificationByHash");
        this.meterRegistry = meterRegistry;
        this.chainLookupTimer = meterRegistry.timer("blockcred.chain.lookup.latency");
        this.chainLookupExecutor = chainLookupExecutor;
        this.chainLookupTimeoutMs = chainLookupTimeoutMs;
    }

    @Transactional(readOnly = true)
    public VerificationResponse verifyPayload(CredentialCanonicalPayload payload) {
        String hash = hashService.generateHash(payload);
        return verifyByHashInternal(hash, true, true, "internal");
    }

    @Transactional(readOnly = true)
    public VerificationResponse verifyCredentialId(String credentialId) {
        CredentialEntity entity = credentialRepository.findByCredentialId(credentialId)
                .orElseThrow(() -> new IllegalArgumentException("Credential not found"));
        return verifyByHashInternal(entity.getHash(), true, true, "internal");
    }

    @Transactional(readOnly = true)
    public VerificationResponse verifyHash(String hash) {
        return verifyByHashInternal(hash, true, true, "internal");
    }

    @Transactional(readOnly = true)
    public VerificationResponse verifyByHash(String hash, boolean payloadHashMatches) {
        return verifyByHashInternal(hash, payloadHashMatches, true, "internal");
    }

    @Transactional(readOnly = true)
    public VerificationResponse verifyHashLive(String hash) {
        return verifyByHashInternal(hash, true, false, "public");
    }

    private VerificationResponse verifyByHashInternal(String hash, boolean payloadHashMatches, boolean useCache, String surface) {
        Timer.Sample verificationSample = Timer.start(meterRegistry);
        if (useCache && payloadHashMatches && verificationCache != null) {
            VerificationResponse cached = verificationCache.get(hash, VerificationResponse.class);
            if (cached != null) {
                incrementVerifyCounter(surface, cached.verificationStatus());
                verificationSample.stop(meterRegistry.timer("blockcred.verify.latency", "surface", surface));
                return cached;
            }
        }

        Optional<CredentialEntity> db = credentialRepository.findByHash(hash);
        ChainLookupResult chain = lookupChain(hash);

        VerificationInput input = new VerificationInput(
                payloadHashMatches,
                db.isPresent(),
                db.map(CredentialEntity::getLifecycleStatus).orElse(null),
                chain.chainReachable(),
                chain.chainRecordFound(),
                chain.chainRevoked()
        );

        VerificationVerdict verdict = resolver.resolve(input);
        AnchoringState anchoringState = db.map(this::anchoringState).orElse(AnchoringState.UNKNOWN);

        VerificationResponse response = new VerificationResponse(
                verdict.status(),
                hash,
                db.isPresent(),
                chain.chainRecordFound(),
                verdict.status() == VerificationStatus.REVOKED,
                anchoringState,
                "BlockCred University",
                chain.txHash(),
                Instant.now(),
                verdict.explanation()
        );
        if (useCache && payloadHashMatches && verificationCache != null) {
            verificationCache.put(hash, response);
        }
        incrementVerifyCounter(surface, response.verificationStatus());
        verificationSample.stop(meterRegistry.timer("blockcred.verify.latency", "surface", surface));
        return response;
    }

    private ChainLookupResult lookupChain(String hash) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            CompletableFuture<ChainLookupResult> call = CompletableFuture.supplyAsync(
                    () -> blockchainGateway.lookup(hash),
                    chainLookupExecutor
            );
            return call.get(chainLookupTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException | ExecutionException e) {
            return ChainLookupResult.unavailable();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ChainLookupResult.unavailable();
        } finally {
            sample.stop(chainLookupTimer);
        }
    }

    private AnchoringState anchoringState(CredentialEntity entity) {
        return switch (entity.getLifecycleStatus()) {
            case ANCHORED, REVOKED -> AnchoringState.ANCHORED;
            case ANCHORING_PENDING -> AnchoringState.PENDING;
            case ANCHOR_FAILED -> AnchoringState.FAILED;
            default -> AnchoringState.UNKNOWN;
        };
    }

    private void incrementVerifyCounter(String surface, VerificationStatus status) {
        meterRegistry.counter(
                "blockcred.verify.requests",
                "surface", surface,
                "status", status.name()
        ).increment();
    }
}

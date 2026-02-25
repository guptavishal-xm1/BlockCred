package com.blockcred.service;

import com.blockcred.api.VerificationResponse;
import com.blockcred.domain.*;
import com.blockcred.infra.CredentialEntity;
import com.blockcred.infra.CredentialRepository;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class VerificationService {
    private final CredentialHashService hashService;
    private final CredentialRepository credentialRepository;
    private final BlockchainGateway blockchainGateway;
    private final VerificationVerdictResolver resolver;
    private final Cache verificationCache;

    public VerificationService(
            CredentialHashService hashService,
            CredentialRepository credentialRepository,
            BlockchainGateway blockchainGateway,
            VerificationVerdictResolver resolver,
            CacheManager cacheManager
    ) {
        this.hashService = hashService;
        this.credentialRepository = credentialRepository;
        this.blockchainGateway = blockchainGateway;
        this.resolver = resolver;
        this.verificationCache = cacheManager.getCache("verificationByHash");
    }

    public VerificationResponse verifyPayload(CredentialCanonicalPayload payload) {
        String hash = hashService.generateHash(payload);
        return verifyByHash(hash, true);
    }

    public VerificationResponse verifyCredentialId(String credentialId) {
        CredentialEntity entity = credentialRepository.findByCredentialId(credentialId)
                .orElseThrow(() -> new IllegalArgumentException("Credential not found"));
        return verifyByHash(entity.getHash(), true);
    }

    public VerificationResponse verifyHash(String hash) {
        return verifyByHash(hash, true);
    }

    public VerificationResponse verifyByHash(String hash, boolean payloadHashMatches) {
        if (payloadHashMatches && verificationCache != null) {
            VerificationResponse cached = verificationCache.get(hash, VerificationResponse.class);
            if (cached != null) {
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
        if (payloadHashMatches && verificationCache != null) {
            verificationCache.put(hash, response);
        }
        return response;
    }

    private ChainLookupResult lookupChain(String hash) {
        try {
            return blockchainGateway.lookup(hash);
        } catch (ChainUnavailableException e) {
            return ChainLookupResult.unavailable();
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
}

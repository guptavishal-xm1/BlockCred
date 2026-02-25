package com.blockcred.service;

import com.blockcred.domain.AuditCategory;
import com.blockcred.domain.AuditSeverity;
import com.blockcred.domain.ChainLookupResult;
import com.blockcred.domain.CredentialLifecycleStatus;
import com.blockcred.infra.CredentialEntity;
import com.blockcred.infra.CredentialRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class ConsistencyScannerService {
    private static final Logger log = LoggerFactory.getLogger(ConsistencyScannerService.class);

    private final CredentialRepository credentialRepository;
    private final BlockchainGateway blockchainGateway;
    private final AuditService auditService;
    private final int revocationGraceMinutes;
    private final int scanWindowHours;
    private final Counter anomaliesCounter;

    public ConsistencyScannerService(
            CredentialRepository credentialRepository,
            BlockchainGateway blockchainGateway,
            AuditService auditService,
            MeterRegistry meterRegistry,
            @Value("${blockcred.ops.revocation-grace-minutes:15}") int revocationGraceMinutes,
            @Value("${blockcred.ops.consistency-scan-window-hours:24}") int scanWindowHours
    ) {
        this.credentialRepository = credentialRepository;
        this.blockchainGateway = blockchainGateway;
        this.auditService = auditService;
        this.revocationGraceMinutes = revocationGraceMinutes;
        this.scanWindowHours = scanWindowHours;
        this.anomaliesCounter = meterRegistry.counter("blockcred.consistency.anomalies");
    }

    @Scheduled(fixedDelayString = "#{${blockcred.ops.consistency-scan-interval-minutes:60} * 60000}")
    @Transactional(readOnly = true)
    public void scanRecentCredentials() {
        Instant now = Instant.now();
        Instant windowStart = now.minus(Duration.ofHours(scanWindowHours));
        List<CredentialEntity> recent = credentialRepository.findByUpdatedAtAfter(windowStart);
        int anomalies = 0;

        for (CredentialEntity credential : recent) {
            ChainLookupResult chain = safeLookup(credential.getHash());
            if (!chain.chainReachable()) {
                continue;
            }

            if ((credential.getLifecycleStatus() == CredentialLifecycleStatus.ANCHORED
                    || credential.getLifecycleStatus() == CredentialLifecycleStatus.REVOKED)
                    && !chain.chainRecordFound()) {
                if (recordAnomalyIfNew("CONSISTENCY_CHAIN_MISSING", credential, now, "Credential present locally but missing on chain")) {
                    anomalies++;
                }
            }

            if (credential.getLifecycleStatus() == CredentialLifecycleStatus.REVOKED
                    && chain.chainRecordFound()
                    && !chain.chainRevoked()
                    && credential.getUpdatedAt().isBefore(now.minus(Duration.ofMinutes(revocationGraceMinutes)))) {
                if (recordAnomalyIfNew("CONSISTENCY_REVOKE_NOT_PROPAGATED", credential, now, "Local revoked but chain not revoked")) {
                    anomalies++;
                }
            }

            if (credential.getLifecycleStatus() != CredentialLifecycleStatus.REVOKED
                    && chain.chainRecordFound()
                    && chain.chainRevoked()) {
                if (recordAnomalyIfNew("CONSISTENCY_LOCAL_NOT_REVOKED", credential, now, "Chain revoked but local record not revoked")) {
                    anomalies++;
                }
            }
        }

        if (anomalies > 0) {
            anomaliesCounter.increment(anomalies);
            log.warn("event=consistency_scan_anomalies count={}", anomalies);
        } else {
            log.info("event=consistency_scan_ok scanned={} anomalies=0", recent.size());
        }
    }

    private boolean recordAnomalyIfNew(String action, CredentialEntity credential, Instant now, String message) {
        Instant dedupeCutoff = now.minus(Duration.ofHours(24));
        if (auditService.hasRecentAction(credential.getCredentialId(), action, dedupeCutoff)) {
            return false;
        }
        auditService.log(
                action,
                credential.getCredentialId(),
                "consistency-scanner",
                message + " hash=" + credential.getHash(),
                AuditSeverity.WARN,
                AuditCategory.CONSISTENCY
        );
        return true;
    }

    private ChainLookupResult safeLookup(String hash) {
        try {
            return blockchainGateway.lookup(hash);
        } catch (Exception e) {
            return ChainLookupResult.unavailable();
        }
    }
}

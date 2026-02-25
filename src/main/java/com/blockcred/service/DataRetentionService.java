package com.blockcred.service;

import com.blockcred.infra.AuditDigestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
public class DataRetentionService {
    private static final Logger log = LoggerFactory.getLogger(DataRetentionService.class);

    private final AuditService auditService;
    private final AuditDigestRepository auditDigestRepository;
    private final int auditRetentionDays;
    private final int digestRetentionDays;

    public DataRetentionService(
            AuditService auditService,
            AuditDigestRepository auditDigestRepository,
            @Value("${blockcred.audit.retention-days:365}") int auditRetentionDays,
            @Value("${blockcred.audit.digest-retention-days:400}") int digestRetentionDays
    ) {
        this.auditService = auditService;
        this.auditDigestRepository = auditDigestRepository;
        this.auditRetentionDays = auditRetentionDays;
        this.digestRetentionDays = digestRetentionDays;
    }

    @Scheduled(cron = "${blockcred.audit.retention-cron:0 30 1 * * *}", zone = "UTC")
    @Transactional
    public void purgeOldData() {
        Instant auditCutoff = Instant.now().minus(Duration.ofDays(auditRetentionDays));
        Instant digestCutoff = Instant.now().minus(Duration.ofDays(digestRetentionDays));

        long deletedLogs = auditService.deleteOlderThan(auditCutoff);
        long deletedDigests = auditDigestRepository.deleteByCreatedAtBefore(digestCutoff);

        log.info("event=data_retention_purge deletedLogs={} deletedDigests={} auditCutoff={} digestCutoff={}",
                deletedLogs, deletedDigests, auditCutoff, digestCutoff);
    }
}

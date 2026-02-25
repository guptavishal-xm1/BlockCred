package com.blockcred.service;

import com.blockcred.domain.AuditCategory;
import com.blockcred.domain.AuditSeverity;
import com.blockcred.infra.AuditDigestEntity;
import com.blockcred.infra.AuditDigestRepository;
import com.blockcred.infra.AuditLogEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AuditDigestService {
    private static final String ACTION_AUDIT_DIGEST_CREATED = "AUDIT_DIGEST_CREATED";

    private final AuditService auditService;
    private final AuditDigestRepository auditDigestRepository;
    private final byte[] digestSecret;

    public AuditDigestService(
            AuditService auditService,
            AuditDigestRepository auditDigestRepository,
            @Value("${blockcred.audit.digest-secret:blockcred-audit-dev-secret-change-me}") String digestSecret
    ) {
        this.auditService = auditService;
        this.auditDigestRepository = auditDigestRepository;
        this.digestSecret = digestSecret.getBytes(StandardCharsets.UTF_8);
    }

    @Scheduled(cron = "${blockcred.audit.digest-cron:0 15 0 * * *}", zone = "UTC")
    @Transactional
    public void createDailyDigest() {
        LocalDate digestDate = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        createDigestForDate(digestDate);
    }

    @Transactional
    public AuditDigestEntity createDigestForDate(LocalDate digestDate) {
        Instant from = digestDate.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant to = digestDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        List<AuditLogEntity> logs = auditService.byCreatedAtRange(from, to).stream()
                .filter(log -> !ACTION_AUDIT_DIGEST_CREATED.equals(log.getAction()))
                .collect(Collectors.toList());
        String canonical = canonical(logs);
        String digestHex = hmacSha256(canonical);

        AuditDigestEntity digest = auditDigestRepository.findByDigestDate(digestDate).orElseGet(AuditDigestEntity::new);
        digest.setDigestDate(digestDate);
        digest.setRecordCount(logs.size());
        digest.setDigestHex(digestHex);
        AuditDigestEntity saved = auditDigestRepository.save(digest);

        auditService.log(
                ACTION_AUDIT_DIGEST_CREATED,
                "SYSTEM",
                "digest-job",
                "date=" + digestDate + ",records=" + logs.size(),
                AuditSeverity.INFO,
                AuditCategory.SYSTEM
        );
        return saved;
    }

    @Transactional(readOnly = true)
    public AuditDigestEntity latestDigest() {
        return auditDigestRepository.findTopByOrderByDigestDateDesc().orElse(null);
    }

    private String canonical(List<AuditLogEntity> logs) {
        StringBuilder sb = new StringBuilder();
        for (AuditLogEntity log : logs) {
            sb.append(log.getId()).append('|')
                    .append(nullSafe(log.getAction())).append('|')
                    .append(nullSafe(log.getCredentialId())).append('|')
                    .append(nullSafe(log.getActor())).append('|')
                    .append(log.getSeverity()).append('|')
                    .append(log.getCategory()).append('|')
                    .append(nullSafe(log.getRequestId())).append('|')
                    .append(nullSafe(log.getDetails())).append('|')
                    .append(log.getCreatedAt())
                    .append('\n');
        }
        return sb.toString();
    }

    private String hmacSha256(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(digestSecret, "HmacSHA256"));
            byte[] digest = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to compute audit digest", e);
        }
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}

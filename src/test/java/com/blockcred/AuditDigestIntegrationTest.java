package com.blockcred;

import com.blockcred.domain.AuditCategory;
import com.blockcred.domain.AuditSeverity;
import com.blockcred.infra.AuditDigestRepository;
import com.blockcred.infra.AuditLogRepository;
import com.blockcred.service.AuditDigestService;
import com.blockcred.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
class AuditDigestIntegrationTest {
    @Autowired
    private AuditService auditService;
    @Autowired
    private AuditDigestService auditDigestService;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private AuditDigestRepository auditDigestRepository;

    @BeforeEach
    void setup() {
        auditDigestRepository.deleteAll();
        auditLogRepository.deleteAll();
    }

    @Test
    void dailyDigestShouldBeDeterministicForSameDayData() {
        auditService.log("ISSUED", "CRED-DIGEST-001", "issuer", "hash-a", AuditSeverity.INFO, AuditCategory.CREDENTIAL);
        auditService.log("ANCHORED", "CRED-DIGEST-001", "worker", "tx-a", AuditSeverity.INFO, AuditCategory.CREDENTIAL);

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String first = auditDigestService.createDigestForDate(today).getDigestHex();
        String second = auditDigestService.createDigestForDate(today).getDigestHex();

        assertNotNull(first);
        assertEquals(first, second);
        assertEquals(1, auditDigestRepository.findAll().size());
    }
}

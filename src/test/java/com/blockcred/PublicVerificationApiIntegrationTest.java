package com.blockcred;

import com.blockcred.domain.CredentialCanonicalPayload;
import com.blockcred.infra.AnchorJobRepository;
import com.blockcred.infra.AuditLogRepository;
import com.blockcred.infra.CredentialRepository;
import com.blockcred.service.CredentialService;
import com.blockcred.service.InMemoryBlockchainGateway;
import com.blockcred.service.JobService;
import com.blockcred.service.VerificationTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
@AutoConfigureMockMvc
class PublicVerificationApiIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CredentialService credentialService;
    @Autowired
    private JobService jobService;
    @Autowired
    private VerificationTokenService verificationTokenService;
    @Autowired
    private CredentialRepository credentialRepository;
    @Autowired
    private AnchorJobRepository anchorJobRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private InMemoryBlockchainGateway blockchainGateway;
    @Autowired
    private CacheManager cacheManager;

    private CredentialCanonicalPayload payload;

    @BeforeEach
    void setup() {
        auditLogRepository.deleteAll();
        anchorJobRepository.deleteAll();
        credentialRepository.deleteAll();
        blockchainGateway.clear();
        cacheManager.getCache("verificationByHash").clear();
        blockchainGateway.setUnavailable(false);

        payload = new CredentialCanonicalPayload(
                "CRED-PUBLIC-001", "UNI-001", "STU-001", "Computer Science", "B.Tech",
                LocalDate.of(2026, 2, 25), "550e8400-e29b-41d4-a716-446655449999", "1.0");
    }

    @Test
    void shouldReturnPublicVerificationForValidToken() throws Exception {
        String hash = credentialService.createAndQueueAnchor(payload).hash();
        jobService.processDueJobs();

        String token = verificationTokenService.sign(hash).token();

        String body = mockMvc.perform(get("/api/public/verify").param("t", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        assertEquals("VALID", json.get("verificationStatus").asText());
        assertEquals("Credential verified and active.", json.get("verdictHeadline").asText());
        assertEquals("token-based verification", json.get("referenceContext").asText());
        assertNotNull(json.get("checkedAt").asText());
    }

    @Test
    void malformedExpiredAndMissingTokenShouldReturnSameNeutralUnresolvedShape() throws Exception {
        VerificationTokenService expiredSigner = new VerificationTokenService(
                "blockcred-dev-secret-change-me",
                "blockcred",
                0,
                Clock.fixed(Instant.now().minusSeconds(3600), ZoneOffset.UTC)
        );

        String malformedBody = mockMvc.perform(get("/api/public/verify").param("t", "bad.token"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String expiredBody = mockMvc.perform(get("/api/public/verify").param("t", expiredSigner.sign("hash-xyz").token()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String missingBody = mockMvc.perform(get("/api/public/verify"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode malformed = objectMapper.readTree(malformedBody);
        JsonNode expired = objectMapper.readTree(expiredBody);
        JsonNode missing = objectMapper.readTree(missingBody);

        assertEquals("NOT_FOUND", malformed.get("verificationStatus").asText());
        assertEquals("NOT_FOUND", expired.get("verificationStatus").asText());
        assertEquals("NOT_FOUND", missing.get("verificationStatus").asText());

        assertEquals("No verification record found for this reference.", malformed.get("verdictHeadline").asText());
        assertEquals(malformed.get("verdictHeadline").asText(), expired.get("verdictHeadline").asText());
        assertEquals(malformed.get("verdictHeadline").asText(), missing.get("verdictHeadline").asText());

        assertEquals(malformed.get("decisionHint").asText(), expired.get("decisionHint").asText());
        assertEquals(malformed.get("decisionHint").asText(), missing.get("decisionHint").asText());
    }
}

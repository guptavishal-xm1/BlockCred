package com.blockcred;

import com.blockcred.domain.CredentialCanonicalPayload;
import com.blockcred.infra.AnchorJobRepository;
import com.blockcred.infra.AuditLogRepository;
import com.blockcred.infra.CredentialRepository;
import com.blockcred.infra.SystemControlRepository;
import com.blockcred.service.CredentialService;
import com.blockcred.service.InMemoryBlockchainGateway;
import com.blockcred.service.JobService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.task.scheduling.enabled=false",
        "blockcred.auth.admin-token=test-admin-token"
})
@AutoConfigureMockMvc
class OpsControllerIntegrationTest {
    private static final String ADMIN_TOKEN = "test-admin-token";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CredentialService credentialService;
    @Autowired
    private JobService jobService;
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
    @Autowired
    private SystemControlRepository systemControlRepository;

    @BeforeEach
    void setup() {
        systemControlRepository.deleteAll();
        auditLogRepository.deleteAll();
        anchorJobRepository.deleteAll();
        credentialRepository.deleteAll();
        blockchainGateway.clear();
        cacheManager.getCache("verificationByHash").clear();
    }

    @Test
    void adminTokenShouldBeRequiredForOpsEndpoints() throws Exception {
        mockMvc.perform(get("/api/ops/jobs"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/ops/jobs").header("X-Admin-Token", "wrong-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reconcileShouldReturnTypedNoActionableResultWhenAlreadyConfirmed() throws Exception {
        CredentialCanonicalPayload payload = new CredentialCanonicalPayload(
                "CRED-OPS-001", "UNI-001", "STU-001", "Computer Science", "B.Tech",
                LocalDate.of(2026, 2, 25), "550e8400-e29b-41d4-a716-446655440321", "1.0");
        credentialService.createAndQueueAnchor(payload);
        jobService.processDueJobs();

        String body = mockMvc.perform(post("/api/ops/reconcile/{credentialId}", payload.credentialId())
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        assertEquals("NO_ACTIONABLE_JOB", json.get("result").asText());
        assertEquals(payload.credentialId(), json.get("credentialId").asText());
        assertEquals("No action required.", json.get("recommendedAction").asText());
    }

    @Test
    void stateEndpointShouldReturnCredentialAndLatestJob() throws Exception {
        CredentialCanonicalPayload payload = new CredentialCanonicalPayload(
                "CRED-OPS-002", "UNI-001", "STU-001", "Computer Science", "B.Tech",
                LocalDate.of(2026, 2, 25), "550e8400-e29b-41d4-a716-446655440322", "1.0");
        credentialService.createAndQueueAnchor(payload);

        String body = mockMvc.perform(get("/api/ops/credentials/{credentialId}/state", payload.credentialId())
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        assertEquals(payload.credentialId(), json.get("credentialId").asText());
        assertEquals("ANCHORING_PENDING", json.get("lifecycleStatus").asText());
        assertEquals("PENDING", json.get("latestJob").get("status").asText());
    }

    @Test
    void summaryEndpointShouldReturnOperationalSnapshot() throws Exception {
        String body = mockMvc.perform(get("/api/ops/summary")
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        assertEquals(true, json.has("pendingCount"));
        assertEquals(true, json.has("retryableCount"));
        assertEquals(true, json.has("chainReachable"));
        assertEquals(true, json.has("recentAnomalyCount"));
    }

    @Test
    void walletDisableShouldBlockAnchorSubmissionWithWalletDisabledFailureCode() throws Exception {
        CredentialCanonicalPayload payload = new CredentialCanonicalPayload(
                "CRED-OPS-003", "UNI-001", "STU-001", "Computer Science", "B.Tech",
                LocalDate.of(2026, 2, 25), "550e8400-e29b-41d4-a716-446655440323", "1.0");
        credentialService.createAndQueueAnchor(payload);

        mockMvc.perform(post("/api/ops/wallet/disable")
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType("application/json")
                        .content("{\"reason\":\"incident\"}"))
                .andExpect(status().isOk());

        jobService.processDueJobs();

        String body = mockMvc.perform(get("/api/ops/credentials/{credentialId}/state", payload.credentialId())
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        assertEquals("RETRYABLE", json.get("latestJob").get("status").asText());
        assertEquals("WALLET_DISABLED", json.get("latestJob").get("failureCode").asText());
    }

    @Test
    void anomaliesEndpointShouldReturnArray() throws Exception {
        String body = mockMvc.perform(get("/api/ops/anomalies?limit=10")
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        assertEquals(true, json.isArray());
    }
}

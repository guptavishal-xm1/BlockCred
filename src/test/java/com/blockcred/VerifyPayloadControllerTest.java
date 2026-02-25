package com.blockcred;

import com.blockcred.api.VerificationResponse;
import com.blockcred.api.VerifyPayloadRequest;
import com.blockcred.domain.CredentialCanonicalPayload;
import com.blockcred.infra.AnchorJobRepository;
import com.blockcred.infra.AuditLogRepository;
import com.blockcred.infra.CredentialRepository;
import com.blockcred.infra.SystemControlRepository;
import com.blockcred.service.CredentialService;
import com.blockcred.service.InMemoryBlockchainGateway;
import com.blockcred.service.JobService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
@AutoConfigureMockMvc
class VerifyPayloadControllerTest {
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

    private CredentialCanonicalPayload payload;

    @BeforeEach
    void setup() {
        systemControlRepository.deleteAll();
        auditLogRepository.deleteAll();
        anchorJobRepository.deleteAll();
        credentialRepository.deleteAll();
        blockchainGateway.clear();
        blockchainGateway.setUnavailable(false);
        cacheManager.getCache("verificationByHash").clear();

        payload = new CredentialCanonicalPayload(
                "CRED-CTRL-001", "UNI-001", "STU-001", "Computer Science", "B.Tech",
                LocalDate.of(2026, 2, 25), "550e8400-e29b-41d4-a716-446655440222", "1.0");
        credentialService.createAndQueueAnchor(payload);
        jobService.processDueJobs();
    }

    @Test
    void verifyPayloadShouldBeDeterministicForStatusAndHash() throws Exception {
        String body = objectMapper.writeValueAsString(new VerifyPayloadRequest(payload));

        String response1 = mockMvc.perform(post("/api/verify/payload")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String response2 = mockMvc.perform(post("/api/verify/payload")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        VerificationResponse r1 = objectMapper.readValue(response1, VerificationResponse.class);
        VerificationResponse r2 = objectMapper.readValue(response2, VerificationResponse.class);

        assertEquals(r1.verificationStatus(), r2.verificationStatus());
        assertEquals(r1.credentialHash(), r2.credentialHash());
    }
}

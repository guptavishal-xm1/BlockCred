package com.blockcred;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
@AutoConfigureMockMvc
class AuthApiIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        // Seed users are configured for dev defaults and loaded at startup.
    }

    @Test
    void shouldLoginAndAccessMe() throws Exception {
        String loginBody = """
                {
                  "usernameOrEmail": "admin",
                  "password": "AdminPass#2026"
                }
                """;
        String loginResponse = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode loginJson = objectMapper.readTree(loginResponse);
        String accessToken = loginJson.get("accessToken").asText();
        assertNotNull(accessToken);

        String meResponse = mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode me = objectMapper.readTree(meResponse);
        assertEquals("admin", me.get("username").asText());
    }

    @Test
    void shouldRejectInvalidLogin() throws Exception {
        String loginBody = """
                {
                  "usernameOrEmail": "admin",
                  "password": "WrongPass#2026"
                }
                """;
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(loginBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void issuerShouldIssueCredentialUsingBearerToken() throws Exception {
        String loginBody = """
                {
                  "usernameOrEmail": "issuer",
                  "password": "IssuerPass#2026"
                }
                """;
        String loginResponse = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode loginJson = objectMapper.readTree(loginResponse);
        String accessToken = loginJson.get("accessToken").asText();

        String issueBody = """
                {
                  "payload": {
                    "credentialId": "CRED-AUTH-001",
                    "universityId": "UNI-001",
                    "studentId": "STU-001",
                    "program": "Computer Science",
                    "degree": "B.Tech",
                    "issueDate": "2026-02-25",
                    "nonce": "550e8400-e29b-41d4-a716-446655440999",
                    "version": "1.0"
                  }
                }
                """;
        mockMvc.perform(post("/api/credentials")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content(issueBody))
                .andExpect(status().isCreated());
    }

    @Test
    void refreshShouldRotateAndInvalidateOldToken() throws Exception {
        String loginBody = """
                {
                  "usernameOrEmail": "admin",
                  "password": "AdminPass#2026"
                }
                """;
        String loginResponse = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode loginJson = objectMapper.readTree(loginResponse);
        String oldRefreshToken = loginJson.get("refreshToken").asText();

        String refreshBody = objectMapper.writeValueAsString(new TokenRefresh(oldRefreshToken));
        String refreshResponse = mockMvc.perform(post("/api/auth/refresh")
                        .contentType("application/json")
                        .content(refreshBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode refreshed = objectMapper.readTree(refreshResponse);
        String newRefreshToken = refreshed.get("refreshToken").asText();
        assertNotEquals(oldRefreshToken, newRefreshToken);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType("application/json")
                        .content(refreshBody))
                .andExpect(status().isUnauthorized());
    }

    private record TokenRefresh(String refreshToken) {
    }
}

package com.blockcred;

import com.blockcred.domain.CredentialCanonicalPayload;
import com.blockcred.service.CanonicalPayloadSerializer;
import com.blockcred.service.CredentialHashService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CanonicalHashServiceTest {
    private final CredentialHashService hashService = new CredentialHashService(new CanonicalPayloadSerializer());

    @Test
    void goldenVectorShouldMatchCanonicalJsonAndHash() {
        CredentialCanonicalPayload payload = new CredentialCanonicalPayload(
                "CRED-001",
                "UNI-001",
                "STU-001",
                "Computer Science",
                "B.Tech",
                LocalDate.of(2026, 2, 25),
                "550e8400-e29b-41d4-a716-446655440000",
                "1.0"
        );

        String expectedCanonical = "{\"credentialId\":\"CRED-001\",\"universityId\":\"UNI-001\",\"studentId\":\"STU-001\",\"program\":\"Computer Science\",\"degree\":\"B.Tech\",\"issueDate\":\"2026-02-25\",\"nonce\":\"550e8400-e29b-41d4-a716-446655440000\",\"version\":\"1.0\"}";
        String expectedHash = "9a1ebcf321a70ac5afd6faa003fbd1590bffbe93546477e53a0a362fdc4c52ba";

        assertEquals(expectedCanonical, hashService.canonicalJson(payload));
        assertEquals(expectedHash, hashService.generateHash(payload));
    }
}

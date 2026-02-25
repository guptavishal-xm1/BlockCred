package com.blockcred;

import com.blockcred.service.VerificationTokenService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class VerificationTokenServiceTest {

    @Test
    void shouldProduceDeterministicTokenForSameInputsAndClock() {
        Clock fixed = Clock.fixed(Instant.parse("2026-02-25T00:00:00Z"), ZoneOffset.UTC);
        VerificationTokenService one = new VerificationTokenService("secret-key", "blockcred", 180, fixed);
        VerificationTokenService two = new VerificationTokenService("secret-key", "blockcred", 180, fixed);

        String tokenOne = one.sign("hash-123").token();
        String tokenTwo = two.sign("hash-123").token();

        assertEquals(tokenOne, tokenTwo);
        assertTrue(one.parseAndVerify(tokenOne).isPresent());
        assertEquals("hash-123", one.parseAndVerify(tokenOne).orElseThrow().credentialHash());
    }

    @Test
    void shouldNormalizeBase64PaddingForSignatureSegment() {
        Clock fixed = Clock.fixed(Instant.parse("2026-02-25T00:00:00Z"), ZoneOffset.UTC);
        VerificationTokenService service = new VerificationTokenService("secret-key", "blockcred", 180, fixed);

        String token = service.sign("hash-123").token();
        String[] parts = token.split("\\.");
        String paddedToken = parts[0] + "." + parts[1] + "." + parts[2] + "=";

        assertTrue(service.parseAndVerify(paddedToken).isPresent());
    }

    @Test
    void shouldRespectExpiryWithSkewTolerance() {
        Instant issuedAt = Instant.parse("2026-02-25T00:00:00Z");
        VerificationTokenService signer = new VerificationTokenService("secret-key", "blockcred", 0,
                Clock.fixed(issuedAt, ZoneOffset.UTC));

        String token = signer.sign("hash-123").token();

        VerificationTokenService withinSkew = new VerificationTokenService("secret-key", "blockcred", 180,
                Clock.fixed(issuedAt.plusSeconds(30), ZoneOffset.UTC));
        VerificationTokenService beyondSkew = new VerificationTokenService("secret-key", "blockcred", 180,
                Clock.fixed(issuedAt.plusSeconds(61), ZoneOffset.UTC));

        assertTrue(withinSkew.parseAndVerify(token).isPresent());
        assertTrue(beyondSkew.parseAndVerify(token).isEmpty());
    }

    @Test
    void shouldReturnEmptyForMalformedToken() {
        VerificationTokenService service = new VerificationTokenService("secret-key", "blockcred", 180,
                Clock.systemUTC());

        assertTrue(service.parseAndVerify("not.a.valid.token.value").isEmpty());
    }
}

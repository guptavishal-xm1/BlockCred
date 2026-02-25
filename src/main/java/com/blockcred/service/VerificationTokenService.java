package com.blockcred.service;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class VerificationTokenService {
    private static final long SKEW_TOLERANCE_SECONDS = 60;

    private final ObjectMapper objectMapper;
    private final byte[] secret;
    private final String issuer;
    private final long ttlDays;
    private final Clock clock;

    public VerificationTokenService(
            @Value("${blockcred.public.token-secret:blockcred-dev-secret-change-me}") String secret,
            @Value("${blockcred.public.token-issuer:blockcred}") String issuer,
            @Value("${blockcred.public.token-ttl-days:180}") long ttlDays,
            Clock clock
    ) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.issuer = issuer;
        this.ttlDays = ttlDays;
        this.clock = clock;

        this.objectMapper = new ObjectMapper();
        this.objectMapper.disable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY);
        this.objectMapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    public SignedToken sign(String credentialHash) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(ttlDays, ChronoUnit.DAYS);

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", credentialHash);
        payload.put("iss", issuer);
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", expiresAt.getEpochSecond());

        try {
            String headerEncoded = encodeBase64Url(objectMapper.writeValueAsBytes(header));
            String payloadEncoded = encodeBase64Url(objectMapper.writeValueAsBytes(payload));
            String signingInput = headerEncoded + "." + payloadEncoded;
            byte[] signature = signBytes(signingInput.getBytes(StandardCharsets.UTF_8));
            String token = signingInput + "." + encodeBase64Url(signature);
            return new SignedToken(token, expiresAt);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign verification token", e);
        }
    }

    public Optional<TokenClaims> parseAndVerify(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }

        try {
            String token = rawToken.trim();
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return Optional.empty();
            }

            String signingInput = parts[0] + "." + parts[1];
            byte[] expectedSignature = signBytes(signingInput.getBytes(StandardCharsets.UTF_8));
            byte[] receivedSignature = decodeBase64Url(parts[2]);
            if (!MessageDigest.isEqual(expectedSignature, receivedSignature)) {
                return Optional.empty();
            }

            byte[] payloadJson = decodeBase64Url(parts[1]);
            TokenPayload payload = objectMapper.readValue(payloadJson, TokenPayload.class);

            if (payload.sub() == null || payload.sub().isBlank()) {
                return Optional.empty();
            }
            if (payload.iss() == null || !issuer.equals(payload.iss())) {
                return Optional.empty();
            }

            long nowEpoch = clock.instant().getEpochSecond();
            if (payload.exp() + SKEW_TOLERANCE_SECONDS < nowEpoch) {
                return Optional.empty();
            }

            return Optional.of(new TokenClaims(payload.sub(), Instant.ofEpochSecond(payload.exp())));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private byte[] signBytes(byte[] value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(value);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign token bytes", e);
        }
    }

    private String encodeBase64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private byte[] decodeBase64Url(String value) {
        String normalized = normalizePadding(value);
        return Base64.getUrlDecoder().decode(normalized);
    }

    private String normalizePadding(String value) {
        int remainder = value.length() % 4;
        if (remainder == 0) {
            return value;
        }
        if (remainder == 1) {
            throw new IllegalArgumentException("Invalid base64url input");
        }
        if (remainder == 2) {
            return value + "==";
        }
        return value + "=";
    }

    private record TokenPayload(String sub, String iss, long iat, long exp) {
    }

    public record SignedToken(String token, Instant expiresAt) {
    }

    public record TokenClaims(String credentialHash, Instant expiresAt) {
    }
}

package com.blockcred.service;

import com.blockcred.domain.AuthRole;
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
import java.util.*;

@Service
public class JwtTokenService {
    private static final long SKEW_TOLERANCE_SECONDS = 60;

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String issuer;
    private final String activeKid;
    private final byte[] activeKey;
    private final String previousKid;
    private final byte[] previousKey;
    private final long accessTokenTtlMinutes;

    public JwtTokenService(
            Clock clock,
            @Value("${blockcred.auth.jwt.issuer:blockcred-staff}") String issuer,
            @Value("${blockcred.auth.jwt.active-kid:v1}") String activeKid,
            @Value("${blockcred.auth.jwt.active-key:blockcred-jwt-dev-active-key-change-me}") String activeKey,
            @Value("${blockcred.auth.jwt.previous-kid:}") String previousKid,
            @Value("${blockcred.auth.jwt.previous-key:}") String previousKey,
            @Value("${blockcred.auth.jwt.access-token-ttl-minutes:15}") long accessTokenTtlMinutes
    ) {
        this.clock = clock;
        this.issuer = issuer;
        this.activeKid = activeKid;
        this.activeKey = activeKey.getBytes(StandardCharsets.UTF_8);
        this.previousKid = previousKid == null ? "" : previousKid.trim();
        this.previousKey = previousKey == null || previousKey.isBlank() ? null : previousKey.getBytes(StandardCharsets.UTF_8);
        this.accessTokenTtlMinutes = accessTokenTtlMinutes;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.disable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY);
        this.objectMapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    public SignedAccessToken sign(AuthPrincipal principal) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(accessTokenTtlMinutes, ChronoUnit.MINUTES);

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");
        header.put("kid", activeKid);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", principal.userId().toString());
        payload.put("username", principal.username());
        payload.put("name", principal.displayName());
        payload.put("roles", principal.roles().stream().map(Enum::name).sorted().toList());
        payload.put("iss", issuer);
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", expiresAt.getEpochSecond());

        try {
            String encodedHeader = encodeBase64Url(objectMapper.writeValueAsBytes(header));
            String encodedPayload = encodeBase64Url(objectMapper.writeValueAsBytes(payload));
            String signingInput = encodedHeader + "." + encodedPayload;
            byte[] signature = hmacSha256(signingInput.getBytes(StandardCharsets.UTF_8), activeKey);
            return new SignedAccessToken(signingInput + "." + encodeBase64Url(signature), expiresAt);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign access token", e);
        }
    }

    public Optional<AccessClaims> parseAndVerify(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }

        try {
            String token = rawToken.trim();
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return Optional.empty();
            }

            byte[] headerBytes = decodeBase64Url(parts[0]);
            TokenHeader header = objectMapper.readValue(headerBytes, TokenHeader.class);
            byte[] key = resolveKey(header.kid());
            if (key == null) {
                return Optional.empty();
            }

            String signingInput = parts[0] + "." + parts[1];
            byte[] expectedSignature = hmacSha256(signingInput.getBytes(StandardCharsets.UTF_8), key);
            byte[] receivedSignature = decodeBase64Url(parts[2]);
            if (!MessageDigest.isEqual(expectedSignature, receivedSignature)) {
                return Optional.empty();
            }

            byte[] payloadBytes = decodeBase64Url(parts[1]);
            TokenPayload payload = objectMapper.readValue(payloadBytes, TokenPayload.class);
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
            Set<AuthRole> roles = parseRoles(payload.roles());
            if (roles.isEmpty()) {
                return Optional.empty();
            }
            Long userId = Long.parseLong(payload.sub());
            return Optional.of(new AccessClaims(userId, payload.username(), payload.name(), roles, Instant.ofEpochSecond(payload.exp())));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Set<AuthRole> parseRoles(List<String> rawRoles) {
        if (rawRoles == null || rawRoles.isEmpty()) {
            return Set.of();
        }
        Set<AuthRole> roles = new HashSet<>();
        for (String role : rawRoles) {
            try {
                roles.add(AuthRole.valueOf(role));
            } catch (Exception ignored) {
                return Set.of();
            }
        }
        return roles;
    }

    private byte[] resolveKey(String kid) {
        if (kid == null || kid.isBlank() || activeKid.equals(kid)) {
            return activeKey;
        }
        if (!previousKid.isBlank() && previousKid.equals(kid)) {
            return previousKey;
        }
        return null;
    }

    private byte[] hmacSha256(byte[] input, byte[] key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(input);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign HMAC SHA256", e);
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
            throw new IllegalArgumentException("Invalid base64url");
        }
        if (remainder == 2) {
            return value + "==";
        }
        return value + "=";
    }

    private record TokenHeader(String alg, String typ, String kid) {
    }

    private record TokenPayload(
            String sub,
            String username,
            String name,
            List<String> roles,
            String iss,
            long iat,
            long exp
    ) {
    }

    public record SignedAccessToken(String token, Instant expiresAt) {
    }

    public record AccessClaims(Long userId, String username, String displayName, Set<AuthRole> roles, Instant expiresAt) {
    }
}

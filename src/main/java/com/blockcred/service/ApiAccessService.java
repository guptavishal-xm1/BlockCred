package com.blockcred.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

@Service
public class ApiAccessService {
    private static final String DEFAULT_ADMIN_TOKEN = "blockcred-admin-dev-token-change-me";
    private static final String DEFAULT_ISSUER_TOKEN = "blockcred-issuer-dev-token-change-me";
    private static final String DEFAULT_PUBLIC_TOKEN_SECRET = "blockcred-dev-secret-change-me";
    private static final String DEFAULT_JWT_ACTIVE_KEY = "blockcred-jwt-dev-active-key-change-me";

    private final String adminToken;
    private final boolean legacyHeaderEnabled;
    private final boolean issuerTokenEnabled;
    private final String issuerToken;
    private final String publicTokenSecret;
    private final String jwtActiveKey;
    private final String walletKeySource;
    private final String walletKeyFilePath;
    private final Environment environment;

    public ApiAccessService(
            @Value("${blockcred.auth.admin-token:" + DEFAULT_ADMIN_TOKEN + "}") String adminToken,
            @Value("${blockcred.auth.legacy-header-enabled:true}") boolean legacyHeaderEnabled,
            @Value("${blockcred.auth.issuer-token-enabled:false}") boolean issuerTokenEnabled,
            @Value("${blockcred.auth.issuer-token:" + DEFAULT_ISSUER_TOKEN + "}") String issuerToken,
            @Value("${blockcred.public.token-secret:" + DEFAULT_PUBLIC_TOKEN_SECRET + "}") String publicTokenSecret,
            @Value("${blockcred.auth.jwt.active-key:" + DEFAULT_JWT_ACTIVE_KEY + "}") String jwtActiveKey,
            @Value("${blockcred.wallet.key-source:ENV}") String walletKeySource,
            @Value("${blockcred.wallet.key-file-path:}") String walletKeyFilePath,
            Environment environment
    ) {
        this.adminToken = adminToken;
        this.legacyHeaderEnabled = legacyHeaderEnabled;
        this.issuerTokenEnabled = issuerTokenEnabled;
        this.issuerToken = issuerToken;
        this.publicTokenSecret = publicTokenSecret;
        this.jwtActiveKey = jwtActiveKey;
        this.walletKeySource = walletKeySource;
        this.walletKeyFilePath = walletKeyFilePath;
        this.environment = environment;
    }

    @PostConstruct
    void validateSecretsOutsideDevProfiles() {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length == 0) {
            return;
        }

        boolean devOrTest = Arrays.stream(activeProfiles)
                .anyMatch(p -> "dev".equalsIgnoreCase(p) || "test".equalsIgnoreCase(p));
        if (devOrTest) {
            return;
        }

        if (DEFAULT_ADMIN_TOKEN.equals(adminToken)) {
            throw new IllegalStateException("Non-dev profile requires blockcred.auth.admin-token override");
        }
        if (issuerTokenEnabled && DEFAULT_ISSUER_TOKEN.equals(issuerToken)) {
            throw new IllegalStateException("Issuer token enabled but blockcred.auth.issuer-token is default");
        }
        if (DEFAULT_PUBLIC_TOKEN_SECRET.equals(publicTokenSecret)) {
            throw new IllegalStateException("Non-dev profile requires blockcred.public.token-secret override");
        }
        if (DEFAULT_JWT_ACTIVE_KEY.equals(jwtActiveKey)) {
            throw new IllegalStateException("Non-dev profile requires blockcred.auth.jwt.active-key override");
        }
        if (!walletKeyPresent()) {
            throw new IllegalStateException("Non-dev profile requires wallet key material (ENV/FILE) configured");
        }
    }

    public void requireAdmin(String providedToken) {
        if (hasRole("ROLE_ADMIN")) {
            return;
        }
        if (!legacyHeaderEnabled) {
            throw forbidden();
        }
        if (!matchesAdminToken(providedToken)) {
            throw forbidden();
        }
    }

    public void requireIssuer(String providedToken) {
        if (hasRole("ROLE_ADMIN") || hasRole("ROLE_ISSUER")) {
            return;
        }
        if (!legacyHeaderEnabled) {
            throw forbidden();
        }
        if (matchesAdminToken(providedToken)) {
            return;
        }
        if (issuerTokenEnabled && matchesIssuerToken(providedToken)) {
            return;
        }
        throw forbidden();
    }

    public boolean isLegacyHeaderEnabled() {
        return legacyHeaderEnabled;
    }

    public boolean matchesAdminToken(String providedToken) {
        if (adminToken == null || adminToken.isBlank() || providedToken == null || providedToken.isBlank()) {
            return false;
        }
        return secureEquals(adminToken, providedToken);
    }

    public boolean matchesIssuerToken(String providedToken) {
        if (!issuerTokenEnabled) {
            return false;
        }
        if (issuerToken == null || issuerToken.isBlank() || providedToken == null || providedToken.isBlank()) {
            return false;
        }
        return secureEquals(issuerToken, providedToken);
    }

    private boolean secureEquals(String expected, String provided) {
        byte[] expectedDigest = sha256(expected == null ? "" : expected);
        byte[] providedDigest = sha256(provided == null ? "" : provided);
        return MessageDigest.isEqual(expectedDigest, providedDigest);
    }

    private byte[] sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
    }

    private boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return false;
        }
        return auth.getAuthorities().stream().anyMatch(granted -> role.equals(granted.getAuthority()));
    }

    private boolean walletKeyPresent() {
        String source = walletKeySource == null ? "" : walletKeySource.trim().toUpperCase();
        if ("FILE".equals(source)) {
            return walletKeyFilePath != null && !walletKeyFilePath.isBlank() && Files.exists(Path.of(walletKeyFilePath));
        }
        String env = System.getenv("BLOCKCRED_WALLET_KEY");
        return env != null && !env.isBlank();
    }
}

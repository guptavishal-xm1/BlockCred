package com.blockcred.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
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

    private final String adminToken;
    private final boolean issuerTokenEnabled;
    private final String issuerToken;
    private final String publicTokenSecret;
    private final String walletKeySource;
    private final String walletKeyFilePath;
    private final Environment environment;

    public ApiAccessService(
            @Value("${blockcred.auth.admin-token:" + DEFAULT_ADMIN_TOKEN + "}") String adminToken,
            @Value("${blockcred.auth.issuer-token-enabled:false}") boolean issuerTokenEnabled,
            @Value("${blockcred.auth.issuer-token:" + DEFAULT_ISSUER_TOKEN + "}") String issuerToken,
            @Value("${blockcred.public.token-secret:" + DEFAULT_PUBLIC_TOKEN_SECRET + "}") String publicTokenSecret,
            @Value("${blockcred.wallet.key-source:ENV}") String walletKeySource,
            @Value("${blockcred.wallet.key-file-path:}") String walletKeyFilePath,
            Environment environment
    ) {
        this.adminToken = adminToken;
        this.issuerTokenEnabled = issuerTokenEnabled;
        this.issuerToken = issuerToken;
        this.publicTokenSecret = publicTokenSecret;
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
        if (!walletKeyPresent()) {
            throw new IllegalStateException("Non-dev profile requires wallet key material (ENV/FILE) configured");
        }
    }

    public void requireAdmin(String providedToken) {
        if (!secureEquals(adminToken, providedToken)) {
            throw forbidden();
        }
    }

    public void requireIssuer(String providedToken) {
        if (!issuerTokenEnabled) {
            return;
        }
        if (!secureEquals(issuerToken, providedToken)) {
            throw forbidden();
        }
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

    private boolean walletKeyPresent() {
        String source = walletKeySource == null ? "" : walletKeySource.trim().toUpperCase();
        if ("FILE".equals(source)) {
            return walletKeyFilePath != null && !walletKeyFilePath.isBlank() && Files.exists(Path.of(walletKeyFilePath));
        }
        String env = System.getenv("BLOCKCRED_WALLET_KEY");
        return env != null && !env.isBlank();
    }
}

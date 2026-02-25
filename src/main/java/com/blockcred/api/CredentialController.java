package com.blockcred.api;

import com.blockcred.service.CredentialService;
import com.blockcred.service.ApiAccessService;
import com.blockcred.service.CurrentUserService;
import com.blockcred.service.VerificationTokenService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

@RestController
@RequestMapping("/api/credentials")
public class CredentialController {
    private static final long FAILURE_FLOOR_MS = 120;

    private final CredentialService credentialService;
    private final ApiAccessService apiAccessService;
    private final CurrentUserService currentUserService;
    private final VerificationTokenService verificationTokenService;
    private final String verifierBaseUrl;

    public CredentialController(
            CredentialService credentialService,
            ApiAccessService apiAccessService,
            CurrentUserService currentUserService,
            VerificationTokenService verificationTokenService,
            @Value("${blockcred.public.verifier-base-url:http://localhost:5173}") String verifierBaseUrl
    ) {
        this.credentialService = credentialService;
        this.apiAccessService = apiAccessService;
        this.currentUserService = currentUserService;
        this.verificationTokenService = verificationTokenService;
        this.verifierBaseUrl = verifierBaseUrl;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CredentialResponse issue(
            @Valid @RequestBody IssueCredentialRequest request,
            @RequestHeader(value = "X-Issuer-Token", required = false) String issuerToken
    ) {
        apiAccessService.requireIssuer(issuerToken);
        return credentialService.createAndQueueAnchor(request.payload(), currentUserService.actorOr("issuer"));
    }

    @PostMapping("/{credentialId}/revoke")
    public CredentialResponse revoke(
            @PathVariable String credentialId,
            @RequestHeader(value = "X-Issuer-Token", required = false) String issuerToken
    ) {
        apiAccessService.requireIssuer(issuerToken);
        return credentialService.requestRevoke(credentialId, currentUserService.actorOr("issuer"));
    }

    @PostMapping("/{credentialId}/share-link")
    public ShareLinkResponse shareLink(
            @PathVariable String credentialId,
            @RequestHeader(value = "X-Issuer-Token", required = false) String issuerToken
    ) {
        apiAccessService.requireIssuer(issuerToken);
        long started = System.nanoTime();
        Optional<String> hash = credentialService.findCredentialHashByCredentialId(credentialId);
        if (hash.isEmpty()) {
            applyFailureFloor(started);
            throw new IllegalStateException("Unable to generate share link");
        }

        VerificationTokenService.SignedToken token = verificationTokenService.sign(hash.get());
        String base = verifierBaseUrl.endsWith("/") ? verifierBaseUrl.substring(0, verifierBaseUrl.length() - 1) : verifierBaseUrl;
        String encodedToken = URLEncoder.encode(token.token(), StandardCharsets.UTF_8);
        String verifyUrl = base + "/verify?t=" + encodedToken;
        return new ShareLinkResponse(verifyUrl, token.expiresAt());
    }

    private void applyFailureFloor(long startedNanos) {
        long elapsedMs = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
        long remainingMs = FAILURE_FLOOR_MS - elapsedMs;
        if (remainingMs <= 0) {
            return;
        }

        try {
            Thread.sleep(remainingMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

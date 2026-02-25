package com.blockcred.service;

import com.blockcred.api.PublicVerificationResponse;
import com.blockcred.api.VerificationResponse;
import com.blockcred.domain.AnchoringState;
import com.blockcred.domain.VerificationStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
public class PublicVerificationService {
    private static final String REFERENCE_CONTEXT = "token-based verification";

    private final VerificationTokenService tokenService;
    private final VerificationService verificationService;
    private final Clock clock;
    private final String txExplorerBaseUrl;

    public PublicVerificationService(
            VerificationTokenService tokenService,
            VerificationService verificationService,
            Clock clock,
            @Value("${blockcred.public.tx-explorer-base-url:https://amoy.polygonscan.com/tx}") String txExplorerBaseUrl
    ) {
        this.tokenService = tokenService;
        this.verificationService = verificationService;
        this.clock = clock;
        this.txExplorerBaseUrl = txExplorerBaseUrl;
    }

    public PublicVerificationResponse verifyFromToken(String rawToken) {
        Optional<VerificationTokenService.TokenClaims> claims = tokenService.parseAndVerify(rawToken);
        if (claims.isEmpty()) {
            return unresolvedResponse();
        }

        VerificationResponse response = verificationService.verifyHashLive(claims.get().credentialHash());
        return mapResponse(response);
    }

    private PublicVerificationResponse mapResponse(VerificationResponse response) {
        VerificationStatus status = response.verificationStatus();
        return new PublicVerificationResponse(
                status,
                headline(status),
                decisionHint(status),
                safeIssuer(response.issuer()),
                response.checkedAt(),
                response.credentialHash(),
                blockchainConfirmation(response.anchoringState()),
                response.txHash(),
                txExplorerUrl(response.txHash()),
                explanation(response),
                REFERENCE_CONTEXT
        );
    }

    private PublicVerificationResponse unresolvedResponse() {
        return new PublicVerificationResponse(
                VerificationStatus.NOT_FOUND,
                headline(VerificationStatus.NOT_FOUND),
                decisionHint(VerificationStatus.NOT_FOUND),
                "Unknown issuer",
                Instant.now(clock),
                null,
                "Blockchain confirmation unavailable",
                null,
                null,
                "No verification record found for this reference.",
                REFERENCE_CONTEXT
        );
    }

    private String headline(VerificationStatus status) {
        return switch (status) {
            case VALID -> "Credential verified and active.";
            case REVOKED -> "Credential was revoked by issuer.";
            case TAMPERED -> "Submitted data does not match trusted record.";
            case PENDING_ANCHOR -> "Issuer record exists; blockchain confirmation pending.";
            case CHAIN_UNAVAILABLE -> "Blockchain check temporarily unavailable; retry shortly.";
            case NOT_FOUND -> "No verification record found for this reference.";
        };
    }

    private String decisionHint(VerificationStatus status) {
        return switch (status) {
            case VALID -> "Proceed with normal review.";
            case REVOKED -> "Do not accept this credential without issuer clarification.";
            case TAMPERED -> "Request a fresh credential artifact from the issuer.";
            case PENDING_ANCHOR -> "Hold decision and retry shortly.";
            case CHAIN_UNAVAILABLE -> "Retry verification before final decision.";
            case NOT_FOUND -> "Please recheck the link or credential data and try again.";
        };
    }

    private String blockchainConfirmation(AnchoringState state) {
        if (state == null) {
            return "Blockchain confirmation unavailable";
        }

        return switch (state) {
            case ANCHORED -> "Confirmed on blockchain";
            case PENDING -> "Blockchain confirmation pending";
            case FAILED -> "Blockchain confirmation pending retry";
            case UNKNOWN -> "Blockchain confirmation unavailable";
        };
    }

    private String explanation(VerificationResponse response) {
        if (response.explanation() == null || response.explanation().isBlank()) {
            return "No additional notes.";
        }
        return response.explanation();
    }

    private String safeIssuer(String issuer) {
        if (issuer == null || issuer.isBlank()) {
            return "Unknown issuer";
        }
        return issuer;
    }

    private String txExplorerUrl(String txHash) {
        if (txHash == null || txHash.isBlank()) {
            return null;
        }

        String base = txExplorerBaseUrl == null ? "" : txExplorerBaseUrl.trim();
        if (base.isEmpty()) {
            return null;
        }
        if (base.endsWith("/")) {
            return base + txHash;
        }
        return base + "/" + txHash;
    }
}

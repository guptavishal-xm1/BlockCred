package com.blockcred.api;

import com.blockcred.domain.VerificationStatus;

import java.time.Instant;

public record PublicVerificationResponse(
        VerificationStatus verificationStatus,
        String verdictHeadline,
        String decisionHint,
        String issuer,
        Instant checkedAt,
        String credentialHash,
        String blockchainConfirmation,
        String txHash,
        String txExplorerUrl,
        String explanation,
        String referenceContext
) {
}

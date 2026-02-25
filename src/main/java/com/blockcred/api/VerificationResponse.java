package com.blockcred.api;

import com.blockcred.domain.AnchoringState;
import com.blockcred.domain.VerificationStatus;

import java.time.Instant;

public record VerificationResponse(
        VerificationStatus verificationStatus,
        String credentialHash,
        boolean dbRecordFound,
        boolean chainRecordFound,
        boolean revoked,
        AnchoringState anchoringState,
        String issuer,
        String txHash,
        Instant checkedAt,
        String explanation
) {
}

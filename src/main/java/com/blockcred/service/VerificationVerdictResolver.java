package com.blockcred.service;

import com.blockcred.domain.*;
import org.springframework.stereotype.Component;

@Component
public class VerificationVerdictResolver {
    public VerificationVerdict resolve(VerificationInput input) {
        if (!input.payloadHashMatches()) {
            return new VerificationVerdict(VerificationStatus.TAMPERED, "Payload hash mismatch");
        }

        if (!input.dbRecordFound() && !input.chainRecordFound()) {
            return new VerificationVerdict(VerificationStatus.NOT_FOUND, "No credential found in local or chain records");
        }

        if (!input.chainReachable()) {
            if (input.dbLifecycleStatus() == CredentialLifecycleStatus.ANCHORING_PENDING
                    || input.dbLifecycleStatus() == CredentialLifecycleStatus.ANCHOR_FAILED) {
                return new VerificationVerdict(VerificationStatus.PENDING_ANCHOR, "Credential exists locally but chain confirmation is pending/unavailable");
            }
            return new VerificationVerdict(VerificationStatus.CHAIN_UNAVAILABLE, "Chain is temporarily unreachable");
        }

        if (input.chainRecordFound() && input.chainRevoked()) {
            return new VerificationVerdict(VerificationStatus.REVOKED, "Credential is revoked on-chain");
        }

        if (input.dbLifecycleStatus() == CredentialLifecycleStatus.REVOKED && !input.chainRevoked()) {
            return new VerificationVerdict(VerificationStatus.REVOKED, "Credential revoked locally; on-chain propagation pending");
        }

        if (input.chainRecordFound() && !input.chainRevoked()) {
            return new VerificationVerdict(VerificationStatus.VALID, "Credential verified successfully");
        }

        return new VerificationVerdict(VerificationStatus.NOT_FOUND, "Credential could not be verified");
    }
}

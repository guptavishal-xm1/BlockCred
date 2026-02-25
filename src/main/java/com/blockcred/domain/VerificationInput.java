package com.blockcred.domain;

public record VerificationInput(
        boolean payloadHashMatches,
        boolean dbRecordFound,
        CredentialLifecycleStatus dbLifecycleStatus,
        boolean chainReachable,
        boolean chainRecordFound,
        boolean chainRevoked
) {
}

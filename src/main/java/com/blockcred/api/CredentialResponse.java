package com.blockcred.api;

import com.blockcred.domain.CredentialLifecycleStatus;

public record CredentialResponse(
        String credentialId,
        String hash,
        CredentialLifecycleStatus status
) {
}

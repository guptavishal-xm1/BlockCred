package com.blockcred.api;

import com.blockcred.domain.CredentialCanonicalPayload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record IssueCredentialRequest(
        @NotNull @Valid CredentialCanonicalPayload payload
) {
}

package com.blockcred.domain;

public record VerificationVerdict(
        VerificationStatus status,
        String explanation
) {
}

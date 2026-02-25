package com.blockcred.domain;

public enum VerificationStatus {
    VALID,
    REVOKED,
    NOT_FOUND,
    TAMPERED,
    PENDING_ANCHOR,
    CHAIN_UNAVAILABLE
}

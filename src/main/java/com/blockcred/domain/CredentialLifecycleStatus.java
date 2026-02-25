package com.blockcred.domain;

public enum CredentialLifecycleStatus {
    DRAFT,
    APPROVED,
    ISSUED,
    ANCHORING_PENDING,
    ANCHORED,
    REVOKED,
    ANCHOR_FAILED
}

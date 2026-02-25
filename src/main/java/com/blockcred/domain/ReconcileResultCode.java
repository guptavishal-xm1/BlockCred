package com.blockcred.domain;

public enum ReconcileResultCode {
    SYNCED,
    QUEUED,
    COOLDOWN_ACTIVE,
    NO_ACTIONABLE_JOB
}

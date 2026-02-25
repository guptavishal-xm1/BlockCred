package com.blockcred.service;

import com.blockcred.domain.CredentialEvent;
import com.blockcred.domain.CredentialLifecycleStatus;
import org.springframework.stereotype.Component;

@Component
public class CredentialStateMachine {

    public CredentialLifecycleStatus transition(CredentialLifecycleStatus current, CredentialEvent event) {
        return switch (event) {
            case APPROVE -> current == CredentialLifecycleStatus.DRAFT ? CredentialLifecycleStatus.APPROVED : reject(current, event);
            case ISSUE -> current == CredentialLifecycleStatus.APPROVED ? CredentialLifecycleStatus.ISSUED : reject(current, event);
            case ANCHOR_QUEUED -> {
                if (current == CredentialLifecycleStatus.ISSUED || current == CredentialLifecycleStatus.ANCHOR_FAILED) {
                    yield CredentialLifecycleStatus.ANCHORING_PENDING;
                }
                if (current == CredentialLifecycleStatus.ANCHORED || current == CredentialLifecycleStatus.REVOKED || current == CredentialLifecycleStatus.ANCHORING_PENDING) {
                    yield current;
                }
                yield reject(current, event);
            }
            case ANCHOR_CONFIRMED -> {
                if (current == CredentialLifecycleStatus.ANCHORING_PENDING || current == CredentialLifecycleStatus.ANCHORED) {
                    yield CredentialLifecycleStatus.ANCHORED;
                }
                if (current == CredentialLifecycleStatus.REVOKED) {
                    yield current;
                }
                yield reject(current, event);
            }
            case ANCHOR_FAILED -> {
                if (current == CredentialLifecycleStatus.ANCHORING_PENDING || current == CredentialLifecycleStatus.ANCHOR_FAILED) {
                    yield CredentialLifecycleStatus.ANCHOR_FAILED;
                }
                if (current == CredentialLifecycleStatus.REVOKED) {
                    yield current;
                }
                yield reject(current, event);
            }
            case REVOKE_REQUESTED, REVOKE_CONFIRMED -> {
                if (current == CredentialLifecycleStatus.ANCHORED
                        || current == CredentialLifecycleStatus.ANCHORING_PENDING
                        || current == CredentialLifecycleStatus.REVOKED) {
                    yield CredentialLifecycleStatus.REVOKED;
                }
                yield reject(current, event);
            }
        };
    }

    private CredentialLifecycleStatus reject(CredentialLifecycleStatus current, CredentialEvent event) {
        throw new IllegalStateException("Illegal transition: " + current + " -> " + event);
    }
}

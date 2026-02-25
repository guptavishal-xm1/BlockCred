package com.blockcred;

import com.blockcred.domain.CredentialEvent;
import com.blockcred.domain.CredentialLifecycleStatus;
import com.blockcred.service.CredentialStateMachine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CredentialStateMachineTest {
    private final CredentialStateMachine machine = new CredentialStateMachine();

    @Test
    void shouldFollowMonotonicTransitions() {
        CredentialLifecycleStatus status = CredentialLifecycleStatus.DRAFT;
        status = machine.transition(status, CredentialEvent.APPROVE);
        status = machine.transition(status, CredentialEvent.ISSUE);
        status = machine.transition(status, CredentialEvent.ANCHOR_QUEUED);
        status = machine.transition(status, CredentialEvent.ANCHOR_CONFIRMED);
        status = machine.transition(status, CredentialEvent.REVOKE_REQUESTED);

        assertEquals(CredentialLifecycleStatus.REVOKED, status);
    }

    @Test
    void shouldBeIdempotentForRepeatedLegalEvents() {
        CredentialLifecycleStatus status = CredentialLifecycleStatus.REVOKED;
        assertEquals(CredentialLifecycleStatus.REVOKED, machine.transition(status, CredentialEvent.REVOKE_CONFIRMED));
        assertEquals(CredentialLifecycleStatus.REVOKED, machine.transition(status, CredentialEvent.ANCHOR_CONFIRMED));
    }

    @Test
    void shouldRejectIllegalTransitions() {
        assertThrows(IllegalStateException.class,
                () -> machine.transition(CredentialLifecycleStatus.DRAFT, CredentialEvent.REVOKE_REQUESTED));
    }
}

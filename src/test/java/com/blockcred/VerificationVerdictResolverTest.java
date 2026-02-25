package com.blockcred;

import com.blockcred.domain.CredentialLifecycleStatus;
import com.blockcred.domain.VerificationInput;
import com.blockcred.domain.VerificationStatus;
import com.blockcred.service.VerificationVerdictResolver;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VerificationVerdictResolverTest {
    private final VerificationVerdictResolver resolver = new VerificationVerdictResolver();

    @Test
    void decisionMatrixShouldBeDeterministic() {
        List<TestCase> cases = List.of(
                new TestCase("tampered", new VerificationInput(false, true, CredentialLifecycleStatus.ANCHORED, true, true, false), VerificationStatus.TAMPERED),
                new TestCase("not_found", new VerificationInput(true, false, null, true, false, false), VerificationStatus.NOT_FOUND),
                new TestCase("pending_anchor_when_chain_down", new VerificationInput(true, true, CredentialLifecycleStatus.ANCHORING_PENDING, false, false, false), VerificationStatus.PENDING_ANCHOR),
                new TestCase("chain_unavailable", new VerificationInput(true, true, CredentialLifecycleStatus.ANCHORED, false, false, false), VerificationStatus.CHAIN_UNAVAILABLE),
                new TestCase("revoked_on_chain", new VerificationInput(true, true, CredentialLifecycleStatus.ANCHORED, true, true, true), VerificationStatus.REVOKED),
                new TestCase("revoked_local_pending_propagation", new VerificationInput(true, true, CredentialLifecycleStatus.REVOKED, true, true, false), VerificationStatus.REVOKED),
                new TestCase("valid", new VerificationInput(true, true, CredentialLifecycleStatus.ANCHORED, true, true, false), VerificationStatus.VALID)
        );

        for (TestCase tc : cases) {
            assertEquals(tc.expected, resolver.resolve(tc.input).status(), tc.name);
        }
    }

    private record TestCase(String name, VerificationInput input, VerificationStatus expected) {
    }
}

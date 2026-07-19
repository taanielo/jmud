package io.taanielo.jmud.core.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;

/**
 * Unit tests for {@link MarriageService}: proposal lifecycle, timeout, and disconnect cleanup.
 */
class MarriageServiceTest {

    private final Username alice = Username.of("Alice");
    private final Username bob = Username.of("Bob");
    private final Username carol = Username.of("Carol");

    @Test
    void proposalIsPendingAgainstTarget() {
        MarriageService service = new MarriageService();
        service.propose(alice, bob);

        assertTrue(service.hasPendingProposal(bob));
        assertEquals(Optional.of(alice), service.pendingProposer(bob));
        assertFalse(service.hasPendingProposal(alice), "proposer holds no pending proposal");
    }

    @Test
    void resolveConsumesTheProposal() {
        MarriageService service = new MarriageService();
        service.propose(alice, bob);

        assertEquals(Optional.of(alice), service.resolve(bob));
        assertFalse(service.hasPendingProposal(bob));
        assertEquals(Optional.empty(), service.resolve(bob), "second resolve finds nothing");
    }

    @Test
    void resolveWithoutProposalReturnsEmpty() {
        MarriageService service = new MarriageService();
        assertEquals(Optional.empty(), service.resolve(bob));
    }

    @Test
    void proposalExpiresAfterTimeoutWindow() {
        MarriageService service = new MarriageService();
        service.propose(alice, bob);

        for (int i = 0; i < MarriageService.DEFAULT_TIMEOUT_TICKS; i++) {
            assertTrue(service.hasPendingProposal(bob), "still pending at tick " + i);
            service.tick();
        }
        assertFalse(service.hasPendingProposal(bob), "proposal lapses after the window");
    }

    @Test
    void clearForRemovesProposalsInvolvingPlayer() {
        MarriageService service = new MarriageService();
        service.propose(alice, bob);
        service.propose(carol, alice);

        // Alice disconnects: her outgoing proposal to Bob and her incoming from Carol both clear.
        service.clearFor(alice);

        assertFalse(service.hasPendingProposal(bob), "Alice's outgoing proposal is cleared");
        assertFalse(service.hasPendingProposal(alice), "Alice's incoming proposal is cleared");
    }

    @Test
    void proposingAgainReplacesEarlierProposal() {
        MarriageService service = new MarriageService();
        service.propose(alice, bob);
        service.propose(carol, bob);

        assertEquals(Optional.of(carol), service.pendingProposer(bob));
    }
}

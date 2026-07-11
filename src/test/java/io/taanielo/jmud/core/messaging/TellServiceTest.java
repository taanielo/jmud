package io.taanielo.jmud.core.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;

/**
 * Unit tests for {@link TellService}.
 */
class TellServiceTest {

    @Test
    void unknownRecipientHasNoLastSender() {
        TellService service = new TellService();
        assertTrue(service.lastSender(Username.of("Nobody")).isEmpty());
    }

    @Test
    void recordsLastSenderPerRecipient() {
        TellService service = new TellService();
        service.recordReceivedTell(Username.of("Bob"), Username.of("Alice"));
        assertEquals(Optional.of(Username.of("Alice")), service.lastSender(Username.of("Bob")));
    }

    @Test
    void mostRecentSenderWins() {
        TellService service = new TellService();
        service.recordReceivedTell(Username.of("Bob"), Username.of("Alice"));
        service.recordReceivedTell(Username.of("Bob"), Username.of("Carol"));
        assertEquals(Optional.of(Username.of("Carol")), service.lastSender(Username.of("Bob")));
    }
}

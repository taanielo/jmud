package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;

/**
 * Unit tests for {@link AfkStatus}, the pure toggle/notice logic behind the {@code AFK} feature.
 */
class AfkStatusTest {

    @Test
    void bareAfkWhenNotAwayTurnsAwayOnWithDefaultMessage() {
        AfkStatus.ToggleResult result = AfkStatus.toggle(false, "");

        assertTrue(result.away());
        assertNull(result.message(), "Default toggle stores no custom message");
        assertEquals("You are now AFK.", result.confirmation());
    }

    @Test
    void bareAfkWhenAwayTurnsAwayOff() {
        AfkStatus.ToggleResult result = AfkStatus.toggle(true, "");

        assertFalse(result.away());
        assertNull(result.message());
        assertEquals("You are no longer AFK.", result.confirmation());
    }

    @Test
    void nullArgsBehavesLikeBareToggle() {
        assertTrue(AfkStatus.toggle(false, null).away());
        assertFalse(AfkStatus.toggle(true, null).away());
    }

    @Test
    void customMessageTurnsAwayOnWithThatReason() {
        AfkStatus.ToggleResult result = AfkStatus.toggle(false, "grabbing coffee");

        assertTrue(result.away());
        assertEquals("grabbing coffee", result.message());
        assertEquals("You are now AFK: grabbing coffee", result.confirmation());
    }

    @Test
    void customMessageWhileAlreadyAwayUpdatesReason() {
        AfkStatus.ToggleResult result = AfkStatus.toggle(true, "back in 5");

        assertTrue(result.away(), "A custom message never turns away off");
        assertEquals("back in 5", result.message());
    }

    @Test
    void customMessageIsTrimmed() {
        AfkStatus.ToggleResult result = AfkStatus.toggle(false, "  lunch  ");

        assertEquals("lunch", result.message());
    }

    @Test
    void recipientNoticeUsesDefaultTextWithoutMessage() {
        assertEquals("Bob is AFK.", AfkStatus.recipientNotice(Username.of("Bob"), null));
        assertEquals("Bob is AFK.", AfkStatus.recipientNotice(Username.of("Bob"), "  "));
    }

    @Test
    void recipientNoticeIncludesCustomMessage() {
        assertEquals("Bob is AFK: grabbing coffee",
            AfkStatus.recipientNotice(Username.of("Bob"), "grabbing coffee"));
    }
}

package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link LfgStatus}, the pure toggle/status/roster logic behind the {@code LFG}
 * feature (issue #510).
 */
class LfgStatusTest {

    @Test
    void bareLfgWhenNotLfgTurnsOnWithDefaultMessage() {
        LfgStatus.ToggleResult result = LfgStatus.toggle(false, "");

        assertTrue(result.lfg());
        assertNull(result.message(), "Default toggle stores no custom note");
        assertEquals("You are now looking for a group.", result.confirmation());
    }

    @Test
    void bareLfgWhenLfgTurnsOff() {
        LfgStatus.ToggleResult result = LfgStatus.toggle(true, "");

        assertFalse(result.lfg());
        assertNull(result.message());
        assertEquals("You are no longer looking for a group.", result.confirmation());
    }

    @Test
    void nullArgsBehavesLikeBareToggle() {
        assertTrue(LfgStatus.toggle(false, null).lfg());
        assertFalse(LfgStatus.toggle(true, null).lfg());
    }

    @Test
    void customNoteTurnsLfgOnWithThatNote() {
        LfgStatus.ToggleResult result = LfgStatus.toggle(false, "tank for Catacombs");

        assertTrue(result.lfg());
        assertEquals("tank for Catacombs", result.message());
        assertEquals("You are now looking for a group: tank for Catacombs", result.confirmation());
    }

    @Test
    void customNoteWhileAlreadyLfgUpdatesNote() {
        LfgStatus.ToggleResult result = LfgStatus.toggle(true, "healer needed");

        assertTrue(result.lfg(), "A custom note never turns LFG off");
        assertEquals("healer needed", result.message());
    }

    @Test
    void customNoteIsTrimmed() {
        LfgStatus.ToggleResult result = LfgStatus.toggle(false, "  dps  ");

        assertEquals("dps", result.message());
    }

    @Test
    void statusArgIsRecognisedCaseInsensitively() {
        assertTrue(LfgStatus.isStatusQuery("STATUS"));
        assertTrue(LfgStatus.isStatusQuery("status"));
        assertTrue(LfgStatus.isStatusQuery("  Status  "));
        assertFalse(LfgStatus.isStatusQuery(""));
        assertFalse(LfgStatus.isStatusQuery(null));
        assertFalse(LfgStatus.isStatusQuery("tank"));
    }

    @Test
    void statusReportsOffWhenNotLfg() {
        assertEquals("You are not looking for a group.", LfgStatus.status(false, null));
        assertEquals("You are not looking for a group.", LfgStatus.status(false, "ignored"));
    }

    @Test
    void statusReportsOnWithAndWithoutNote() {
        assertEquals("You are looking for a group.", LfgStatus.status(true, null));
        assertEquals("You are looking for a group.", LfgStatus.status(true, "  "));
        assertEquals("You are looking for a group: tank for Catacombs",
            LfgStatus.status(true, "tank for Catacombs"));
    }

    @Test
    void rosterTagIsEmptyWhenNotLfg() {
        assertEquals("", LfgStatus.rosterTag(false, null));
        assertEquals("", LfgStatus.rosterTag(false, "ignored"));
    }

    @Test
    void rosterTagUsesDefaultWithoutNote() {
        assertEquals(" [LFG]", LfgStatus.rosterTag(true, null));
        assertEquals(" [LFG]", LfgStatus.rosterTag(true, "  "));
    }

    @Test
    void rosterTagIncludesNoteWhenSet() {
        assertEquals(" [LFG: tank for Catacombs]", LfgStatus.rosterTag(true, "tank for Catacombs"));
    }
}

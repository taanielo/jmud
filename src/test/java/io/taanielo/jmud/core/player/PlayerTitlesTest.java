package io.taanielo.jmud.core.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PlayerTitles} active-title selection and validation.
 */
class PlayerTitlesTest {

    @Test
    void emptyHasNoActiveTitle() {
        assertNull(PlayerTitles.empty().active());
    }

    @Test
    void withActiveSelectsEarnedTitle() {
        PlayerTitles titles = new PlayerTitles(List.of("Centurion", "Slayer")).withActive("Centurion");

        assertEquals("Centurion", titles.active());
    }

    @Test
    void withActiveMatchesCaseInsensitively() {
        PlayerTitles titles = new PlayerTitles(List.of("Centurion")).withActive("cEnTuRiOn");

        assertEquals("Centurion", titles.active());
    }

    @Test
    void withActiveRejectsUnearnedTitle() {
        PlayerTitles titles = new PlayerTitles(List.of("Centurion"));

        assertThrows(IllegalArgumentException.class, () -> titles.withActive("Slayer"));
    }

    @Test
    void clearActiveRemovesSelection() {
        PlayerTitles titles = new PlayerTitles(List.of("Centurion")).withActive("Centurion").clearActive();

        assertNull(titles.active());
    }

    @Test
    void clearActiveReturnsSameInstanceWhenNoneActive() {
        PlayerTitles titles = new PlayerTitles(List.of("Centurion"));

        assertSame(titles, titles.clearActive());
    }

    @Test
    void constructorDropsActiveTitleNotEarned() {
        PlayerTitles titles = new PlayerTitles(List.of("Centurion"), "Ghost");

        assertNull(titles.active());
    }

    @Test
    void constructorNormalizesActiveToEarnedCasing() {
        PlayerTitles titles = new PlayerTitles(List.of("Centurion"), "centurion");

        assertEquals("Centurion", titles.active());
    }

    @Test
    void withEarnedDropsActiveWhenNoLongerPresent() {
        PlayerTitles titles = new PlayerTitles(List.of("Centurion")).withActive("Centurion")
            .withEarned(List.of("Slayer"));

        assertNull(titles.active());
    }

    @Test
    void grantPreservesActiveTitle() {
        PlayerTitles titles = new PlayerTitles(List.of("Centurion")).withActive("Centurion").grant("Slayer");

        assertEquals("Centurion", titles.active());
        assertTrue(titles.has("Slayer"));
    }

    @Test
    void matchEarnedIsCaseInsensitive() {
        PlayerTitles titles = new PlayerTitles(List.of("Centurion"));

        assertEquals("Centurion", titles.matchEarned("CENTURION").orElseThrow());
        assertFalse(titles.matchEarned("Slayer").isPresent());
    }
}

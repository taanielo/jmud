package io.taanielo.jmud.core.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PlayerPreferences}, focusing on the {@code autoLootEnabled} flag: its default
 * (off), its wither, and that unrelated fields are preserved across withers.
 */
class PlayerPreferencesTest {

    @Test
    void autoLootDefaultsOffWhenAbsent() {
        PlayerPreferences prefs = new PlayerPreferences("%hp>", true);

        assertFalse(prefs.autoLootEnabled());
        assertTrue(prefs.ansiEnabled());
    }

    @Test
    void autoLootDefaultsOffWhenNull() {
        PlayerPreferences prefs = new PlayerPreferences("%hp>", true, null);

        assertFalse(prefs.autoLootEnabled());
    }

    @Test
    void withAutoLootEnabledFlipsFlagPreservingOtherFields() {
        PlayerPreferences prefs = new PlayerPreferences("%hp>", true, false);

        PlayerPreferences enabled = prefs.withAutoLootEnabled(true);

        assertTrue(enabled.autoLootEnabled());
        assertTrue(enabled.ansiEnabled());
        assertEquals("%hp>", enabled.promptFormat());
    }

    @Test
    void withAnsiAndPromptPreserveAutoLoot() {
        PlayerPreferences prefs = new PlayerPreferences("%hp>", false, true);

        assertTrue(prefs.withAnsiEnabled(true).autoLootEnabled());
        assertTrue(prefs.withPromptFormat("%h>").autoLootEnabled());
    }

    @Test
    void briefModeDefaultsOffWhenAbsent() {
        PlayerPreferences prefs = new PlayerPreferences("%hp>", true, true);

        assertFalse(prefs.briefModeEnabled());
    }

    @Test
    void briefModeDefaultsOffWhenNull() {
        PlayerPreferences prefs = new PlayerPreferences("%hp>", true, true, null);

        assertFalse(prefs.briefModeEnabled());
    }

    @Test
    void withBriefModeEnabledFlipsFlagPreservingOtherFields() {
        PlayerPreferences prefs = new PlayerPreferences("%hp>", true, true, false);

        PlayerPreferences enabled = prefs.withBriefModeEnabled(true);

        assertTrue(enabled.briefModeEnabled());
        assertTrue(enabled.ansiEnabled());
        assertTrue(enabled.autoLootEnabled());
        assertEquals("%hp>", enabled.promptFormat());
    }

    @Test
    void withAnsiAutoLootAndPromptPreserveBriefMode() {
        PlayerPreferences prefs = new PlayerPreferences("%hp>", false, false, true);

        assertTrue(prefs.withAnsiEnabled(true).briefModeEnabled());
        assertTrue(prefs.withAutoLootEnabled(true).briefModeEnabled());
        assertTrue(prefs.withPromptFormat("%h>").briefModeEnabled());
    }
}

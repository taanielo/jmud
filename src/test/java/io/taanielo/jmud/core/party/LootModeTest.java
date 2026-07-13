package io.taanielo.jmud.core.party;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link LootMode} label rendering and case-insensitive argument parsing, including
 * the {@code roll} mode and its {@code dice}/{@code need} aliases.
 */
class LootModeTest {

    @Test
    void label_rendersPlayerFacingText() {
        assertEquals("free", LootMode.FREE.label());
        assertEquals("round-robin", LootMode.ROUND_ROBIN.label());
        assertEquals("roll", LootMode.ROLL.label());
    }

    @Test
    void parse_acceptsCanonicalLabels() {
        assertEquals(Optional.of(LootMode.FREE), LootMode.parse("free"));
        assertEquals(Optional.of(LootMode.ROUND_ROBIN), LootMode.parse("round-robin"));
        assertEquals(Optional.of(LootMode.ROLL), LootMode.parse("roll"));
    }

    @Test
    void parse_acceptsRollAliasesCaseInsensitively() {
        assertEquals(Optional.of(LootMode.ROLL), LootMode.parse("ROLL"));
        assertEquals(Optional.of(LootMode.ROLL), LootMode.parse("dice"));
        assertEquals(Optional.of(LootMode.ROLL), LootMode.parse("Need"));
        assertEquals(Optional.of(LootMode.ROLL), LootMode.parse("  roll  "));
    }

    @Test
    void parse_rejectsUnknownInput() {
        assertTrue(LootMode.parse("greed").isEmpty());
        assertTrue(LootMode.parse(null).isEmpty());
        assertTrue(LootMode.parse("").isEmpty());
    }
}

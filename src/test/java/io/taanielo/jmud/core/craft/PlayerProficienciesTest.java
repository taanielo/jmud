package io.taanielo.jmud.core.craft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PlayerProficiencies}, covering level derivation, immutability and the
 * string-keyed round-trip used for JSON persistence.
 */
class PlayerProficienciesTest {

    @Test
    void emptyProficienciesReportLevelZero() {
        PlayerProficiencies proficiencies = PlayerProficiencies.empty();

        assertTrue(proficiencies.isEmpty());
        assertEquals(0, proficiencies.points(ProfessionId.BLACKSMITHING));
        assertEquals(0, proficiencies.level(ProfessionId.BLACKSMITHING));
    }

    @Test
    void levelIsPointsDividedByPointsPerLevel() {
        PlayerProficiencies proficiencies =
            PlayerProficiencies.empty().gain(ProfessionId.ALCHEMY, PlayerProficiencies.POINTS_PER_LEVEL * 2 + 40);

        assertEquals(PlayerProficiencies.POINTS_PER_LEVEL * 2 + 40, proficiencies.points(ProfessionId.ALCHEMY));
        assertEquals(2, proficiencies.level(ProfessionId.ALCHEMY));
    }

    @Test
    void gainDoesNotMutateOriginal() {
        PlayerProficiencies original = PlayerProficiencies.empty().gain(ProfessionId.COOKING, 50);

        PlayerProficiencies grown = original.gain(ProfessionId.COOKING, 50);

        assertEquals(50, original.points(ProfessionId.COOKING), "original unchanged");
        assertEquals(100, grown.points(ProfessionId.COOKING));
        assertEquals(1, grown.level(ProfessionId.COOKING));
    }

    @Test
    void nonPositiveGainReturnsSameInstance() {
        PlayerProficiencies original = PlayerProficiencies.empty().gain(ProfessionId.COOKING, 30);

        assertEquals(original, original.gain(ProfessionId.COOKING, 0));
        assertEquals(original, original.gain(ProfessionId.COOKING, -5));
    }

    @Test
    void stringMapRoundTripPreservesPoints() {
        PlayerProficiencies proficiencies = PlayerProficiencies.empty()
            .gain(ProfessionId.BLACKSMITHING, 120)
            .gain(ProfessionId.COOKING, 30);

        Map<String, Integer> raw = proficiencies.toStringMap();
        PlayerProficiencies restored = PlayerProficiencies.fromStringMap(raw);

        assertEquals(120, restored.points(ProfessionId.BLACKSMITHING));
        assertEquals(30, restored.points(ProfessionId.COOKING));
    }

    @Test
    void fromNullStringMapIsEmpty() {
        assertTrue(PlayerProficiencies.fromStringMap(null).isEmpty());
        assertFalse(PlayerProficiencies.fromStringMap(Map.of("blacksmithing", 100)).isEmpty());
    }
}

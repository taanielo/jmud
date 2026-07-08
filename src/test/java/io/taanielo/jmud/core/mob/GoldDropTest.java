package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.combat.SeededCombatRandom;

/**
 * Unit tests for {@link GoldDrop}, verifying that gold amounts are rolled through the
 * seeded RNG port and are therefore deterministic under a fixed seed (AGENTS.md §5).
 */
class GoldDropTest {

    @Test
    void roll_returnsMin_whenMinEqualsMax() {
        GoldDrop drop = new GoldDrop(7, 7);
        assertEquals(7, drop.roll(new SeededCombatRandom(1L)));
    }

    @Test
    void roll_staysWithinRange() {
        GoldDrop drop = new GoldDrop(3, 9);
        SeededCombatRandom random = new SeededCombatRandom(42L);
        for (int i = 0; i < 100; i++) {
            int gold = drop.roll(random);
            assertTrue(gold >= 3 && gold <= 9, "Rolled gold " + gold + " out of range [3, 9]");
        }
    }

    @Test
    void roll_isDeterministic_underFixedSeed() {
        GoldDrop drop = new GoldDrop(1, 1000);
        List<Integer> firstRun = rollSequence(drop, 9999L);
        List<Integer> secondRun = rollSequence(drop, 9999L);
        assertEquals(firstRun, secondRun, "Same seed must produce identical gold sequences");
    }

    @Test
    void roll_rejectsNullRandom() {
        GoldDrop drop = new GoldDrop(1, 2);
        assertThrows(NullPointerException.class, () -> drop.roll(null));
    }

    private List<Integer> rollSequence(GoldDrop drop, long seed) {
        SeededCombatRandom random = new SeededCombatRandom(seed);
        List<Integer> rolls = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            rolls.add(drop.roll(random));
        }
        return rolls;
    }
}

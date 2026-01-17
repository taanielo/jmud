package io.taanielo.jmud.core.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class BasicStatsTest {

    @Test
    void damageAndHealRespectBounds() {
        Stats stats = BasicStats.of(10, 10, 5, 5, 2, 3);
        Stats damaged = stats.damage(7);
        assertEquals(3, damaged.hp());

        Stats healed = damaged.heal(20);
        assertEquals(10, healed.hp());
    }

    @Test
    void manaConsumptionAndRestoreRespectBounds() {
        Stats stats = BasicStats.of(10, 10, 5, 5, 2, 3);
        Stats drained = stats.consumeMana(4);
        assertEquals(1, drained.mana());

        Stats restored = drained.restoreMana(10);
        assertEquals(5, restored.mana());
    }

    @Test
    void rejectsNegativeValues() {
        assertThrows(IllegalArgumentException.class, () -> BasicStats.of(-1, 10, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> BasicStats.of(1, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> BasicStats.of(0, 10, -1, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> BasicStats.of(0, 10, 0, -1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> BasicStats.of(0, 10, 0, 0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> BasicStats.of(0, 10, 0, 0, 0, -1));
    }

    @Test
    void rejectsNegativeMutations() {
        Stats stats = BasicStats.of(5, 5, 5, 5, 1, 1);
        assertThrows(IllegalArgumentException.class, () -> stats.damage(-1));
        assertThrows(IllegalArgumentException.class, () -> stats.heal(-1));
        assertThrows(IllegalArgumentException.class, () -> stats.consumeMana(-1));
        assertThrows(IllegalArgumentException.class, () -> stats.restoreMana(-1));
    }
}

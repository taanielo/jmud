package io.taanielo.jmud.core.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AbilityCostTest {

    @Test
    void storesManaAndMoveCosts() {
        AbilityCost cost = new AbilityCost(5, 2);

        assertEquals(5, cost.mana());
        assertEquals(2, cost.move());
        assertEquals(0, cost.manaPerTarget(),
            "The two-arg constructor defaults per-target mana to zero (single-target abilities)");
    }

    @Test
    void rejectsNegativeMana() {
        assertThrows(IllegalArgumentException.class, () -> new AbilityCost(-1, 0));
    }

    @Test
    void rejectsNegativeMove() {
        assertThrows(IllegalArgumentException.class, () -> new AbilityCost(0, -1));
    }

    @Test
    void rejectsNegativeManaPerTarget() {
        assertThrows(IllegalArgumentException.class, () -> new AbilityCost(0, 0, -1));
    }

    @Test
    void totalManaScalesWithTargetCount() {
        AbilityCost cost = new AbilityCost(4, 0, 2);

        assertEquals(4, cost.totalMana(0), "No targets costs only the base mana");
        assertEquals(6, cost.totalMana(1));
        assertEquals(10, cost.totalMana(3), "base 4 + 2 per target * 3 = 10");
    }

    @Test
    void totalManaRejectsNegativeTargetCount() {
        assertThrows(IllegalArgumentException.class, () -> new AbilityCost(4, 0, 2).totalMana(-1));
    }
}

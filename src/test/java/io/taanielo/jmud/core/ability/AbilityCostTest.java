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
    }

    @Test
    void rejectsNegativeMana() {
        assertThrows(IllegalArgumentException.class, () -> new AbilityCost(-1, 0));
    }

    @Test
    void rejectsNegativeMove() {
        assertThrows(IllegalArgumentException.class, () -> new AbilityCost(0, -1));
    }
}

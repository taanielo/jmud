package io.taanielo.jmud.core.tick.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CooldownSystemTest {

    @Test
    void clearRemovesAllCooldowns() {
        CooldownSystem system = new CooldownSystem();
        system.register("ability", 2);

        assertTrue(system.isOnCooldown("ability"));

        system.clear();

        assertFalse(system.isOnCooldown("ability"));
        assertEquals(0, system.remainingTicks("ability"));
    }
}

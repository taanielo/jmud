package io.taanielo.jmud.core.tick.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CooldownSystemTest {

    @Test
    void cooldownExpiresAfterExactlyRegisteredTicks() {
        CooldownSystem system = new CooldownSystem();
        system.register("ability", 2);

        system.tick();
        assertTrue(system.isOnCooldown("ability"), "still on cooldown after first tick");
        assertEquals(1, system.remainingTicks("ability"));

        system.tick();
        assertFalse(system.isOnCooldown("ability"), "off cooldown after second tick");
        assertEquals(0, system.remainingTicks("ability"));
    }

    @Test
    void tickDecrementsMultipleCooldownsIndependently() {
        CooldownSystem system = new CooldownSystem();
        system.register("short", 1);
        system.register("long", 3);

        system.tick();

        assertFalse(system.isOnCooldown("short"));
        assertTrue(system.isOnCooldown("long"));
        assertEquals(2, system.remainingTicks("long"));
    }

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

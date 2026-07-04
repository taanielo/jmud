package io.taanielo.jmud.core.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.tick.system.CooldownSystem;

class CooldownTrackerTest {

    private final CooldownSystem cooldownSystem = new CooldownSystem();
    private final CooldownTracker tracker = new CooldownTracker(cooldownSystem);
    private final AbilityId abilityId = AbilityId.of("kick");

    @Test
    void abilityIsNotOnCooldownBeforeBeingStarted() {
        assertFalse(tracker.isOnCooldown(abilityId));
        assertEquals(0, tracker.remainingTicks(abilityId));
    }

    @Test
    void startCooldownRegistersRemainingTicks() {
        tracker.startCooldown(abilityId, 3);

        assertTrue(tracker.isOnCooldown(abilityId));
        assertEquals(3, tracker.remainingTicks(abilityId));
    }
}

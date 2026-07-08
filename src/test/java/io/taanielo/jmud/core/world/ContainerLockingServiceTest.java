package io.taanielo.jmud.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.combat.CombatRandom;

/**
 * Unit tests for {@link ContainerLockingService}: pick-success scaling, trap and damage rolls, and
 * container unlocking. All randomness is driven by a scripted {@link CombatRandom} so the outcomes
 * are deterministic (AGENTS.md §5).
 */
class ContainerLockingServiceTest {

    private static Item lockedChest() {
        return Item.builder(ItemId.of("chest"), "a treasure chest", "An iron-banded chest.", ItemAttributes.empty())
            .weight(10)
            .value(100)
            .container(ContainerState.of(3))
            .build()
            .withLocked(true);
    }

    @Test
    void pickSuccessChanceScalesWithLevelAndIsCapped() {
        ContainerLockingService service = new ContainerLockingService(new ScriptedRandom());

        assertEquals(0.72, service.calculatePickSuccessChance(1), 1e-9);
        assertEquals(0.80, service.calculatePickSuccessChance(5), 1e-9);
        // 0.70 + 0.02 * 20 = 1.10, capped at 0.95
        assertEquals(0.95, service.calculatePickSuccessChance(20), 1e-9);
    }

    @Test
    void pickSuccessChanceRejectsLevelBelowOne() {
        ContainerLockingService service = new ContainerLockingService(new ScriptedRandom());

        assertThrows(IllegalArgumentException.class, () -> service.calculatePickSuccessChance(0));
    }

    @Test
    void rollPickSuccessComparesRollAgainstThreshold() {
        // Level 5 => 80% => threshold 80. A roll of 80 succeeds, 81 fails.
        ContainerLockingService success = new ContainerLockingService(new ScriptedRandom(80));
        ContainerLockingService failure = new ContainerLockingService(new ScriptedRandom(81));

        assertTrue(success.rollPickSuccess(5));
        assertFalse(failure.rollPickSuccess(5));
    }

    @Test
    void shouldTrapTriggerRespectsThirtyPercentThreshold() {
        assertTrue(new ContainerLockingService(new ScriptedRandom(30)).shouldTrapTrigger());
        assertFalse(new ContainerLockingService(new ScriptedRandom(31)).shouldTrapTrigger());
    }

    @Test
    void rollTrapDamageReturnsWithinRange() {
        ContainerLockingService service = new ContainerLockingService(new ScriptedRandom(11));

        assertEquals(11, service.rollTrapDamage());
    }

    @Test
    void unlockContainerReturnsUnlockedCopy() {
        ContainerLockingService service = new ContainerLockingService(new ScriptedRandom());
        Item locked = lockedChest();

        Item unlocked = service.unlockContainer(locked);

        assertTrue(locked.isLocked());
        assertFalse(unlocked.isLocked());
        assertEquals(locked.getId(), unlocked.getId());
    }

    @Test
    void unlockContainerRejectsNonContainer() {
        ContainerLockingService service = new ContainerLockingService(new ScriptedRandom());
        Item sword = Item.builder(ItemId.of("sword"), "a sword", "A sword.", ItemAttributes.empty())
            .equipSlot(EquipmentSlot.WEAPON)
            .weight(5)
            .value(10)
            .build();

        assertThrows(IllegalArgumentException.class, () -> service.unlockContainer(sword));
    }

    /**
     * A {@link CombatRandom} that returns scripted values in order, repeating the last value once
     * exhausted (mirroring the pattern used elsewhere in the test suite).
     */
    private static final class ScriptedRandom implements CombatRandom {
        private final int[] values;
        private int index;

        private ScriptedRandom(int... values) {
            this.values = values;
        }

        @Override
        public int roll(int minInclusive, int maxInclusive) {
            if (values.length == 0) {
                return minInclusive;
            }
            if (index >= values.length) {
                return values[values.length - 1];
            }
            return values[index++];
        }
    }
}

package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.combat.AttackId;
import io.taanielo.jmud.core.world.RoomId;

/**
 * Unit tests for the per-encounter enrage state on {@link MobInstance} (issue #745): the AI-decision
 * clock, the one-time enrage transition, the outgoing-damage multiplier, and the reset that fires when
 * the encounter ends (disengage/respawn), mirroring {@code specialAbilityUsed}/telegraph state.
 */
class MobInstanceEnrageTest {

    private static final RoomId SPAWN_ROOM = RoomId.of("spawn");
    private static final AttackId ATTACK_ID = AttackId.of("attack.basic");
    private static final AttackId SPECIAL_ID = AttackId.of("attack.special");

    private MobTemplate enrageTemplate(Integer enrageTicks, double multiplier) {
        return new MobTemplate(
            MobId.of("mob.boss"), "the Boss", 100, ATTACK_ID, SPECIAL_ID, true,
            List.of(), SPAWN_ROOM, 1, 30, 5, null, List.of(), false,
            null, null, false, null, null, false, false, 0, Map.of(), Map.of(), null,
            enrageTicks, multiplier);
    }

    @Test
    void enragesExactlyOnceAfterThresholdDecisions() {
        MobInstance mob = new MobInstance(enrageTemplate(3, 2.0));

        assertFalse(mob.advanceEnrage(), "decision 1 is below the threshold");
        assertFalse(mob.isEnraged());
        assertFalse(mob.advanceEnrage(), "decision 2 is below the threshold");
        assertFalse(mob.isEnraged());
        assertTrue(mob.advanceEnrage(), "decision 3 crosses the threshold and enrages the mob");
        assertTrue(mob.isEnraged());
        assertFalse(mob.advanceEnrage(),
            "an already-enraged mob never re-announces on a subsequent decision");
        assertTrue(mob.isEnraged());
    }

    @Test
    void nonEnrageCapableMobNeverEnrages() {
        MobInstance mob = new MobInstance(enrageTemplate(null, 1.0));

        for (int i = 0; i < 50; i++) {
            assertFalse(mob.advanceEnrage(), "a mob with no enrage threshold never enrages");
        }
        assertFalse(mob.isEnraged());
        assertEquals(10, mob.applyEnrageMultiplier(10),
            "a non-enrage-capable mob's damage is never scaled");
    }

    @Test
    void multiplierAppliesOnlyOnceEnraged() {
        MobInstance mob = new MobInstance(enrageTemplate(1, 2.5));

        assertEquals(10, mob.applyEnrageMultiplier(10), "damage is unscaled before enraging");
        assertTrue(mob.advanceEnrage());
        assertEquals(25, mob.applyEnrageMultiplier(10),
            "an enraged mob scales its landed damage by the authored multiplier");
        assertEquals(1, mob.applyEnrageMultiplier(0),
            "an enraged hit is floored at 1 so it never rounds down to zero");
    }

    @Test
    void disengageResetsTheEnrageClock() {
        MobInstance mob = new MobInstance(enrageTemplate(2, 2.0));
        Username hero = Username.of("hero");
        mob.engage(hero);

        assertFalse(mob.advanceEnrage());
        assertTrue(mob.advanceEnrage(), "two decisions enrage a threshold-2 mob");
        assertTrue(mob.isEnraged());

        mob.disengage(hero);
        assertFalse(mob.isEnraged(),
            "fully disengaging ends the encounter and clears enrage, like specialAbilityUsed");

        // A fresh pull starts the clock over rather than being enraged immediately.
        assertFalse(mob.advanceEnrage(), "the re-engaged clock restarts from zero");
        assertTrue(mob.advanceEnrage());
        assertTrue(mob.isEnraged());
    }

    @Test
    void respawnResetsEnrage() {
        MobInstance mob = new MobInstance(enrageTemplate(1, 2.0));
        assertTrue(mob.advanceEnrage());
        assertTrue(mob.isEnraged());

        mob.respawn();
        assertFalse(mob.isEnraged(), "a respawned mob starts a fresh, un-enraged encounter");
        assertEquals(10, mob.applyEnrageMultiplier(10));
    }

    @Test
    void enragePersistsAcrossSpecialAbilityUse() {
        MobInstance mob = new MobInstance(enrageTemplate(1, 2.0));
        assertTrue(mob.advanceEnrage());

        // Using the special / telegraph state must not clear enrage (only disengage/respawn do).
        mob.markSpecialAbilityUsed();
        assertTrue(mob.isEnraged(),
            "enrage persists through the boss's special attack for the rest of the encounter");
        assertEquals(20, mob.applyEnrageMultiplier(10));
    }
}

package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.combat.AttackId;
import io.taanielo.jmud.core.world.RoomId;

/**
 * Unit tests for the per-encounter reinforcement state on {@link MobInstance} (issue #809): the
 * one-time HP-threshold trigger, and the reset that fires when the encounter ends (disengage/respawn),
 * mirroring the enrage state.
 */
class MobInstanceReinforcementTest {

    private static final RoomId SPAWN_ROOM = RoomId.of("spawn");
    private static final AttackId ATTACK_ID = AttackId.of("attack.basic");

    private MobTemplate reinforcementTemplate(
        Integer hpPercent, MobId addId, int count) {
        return new MobTemplate(
            MobId.of("mob.boss"), "the Boss", 100, ATTACK_ID, null, true,
            List.of(), SPAWN_ROOM, 1, 30, 5, null, List.of(), false,
            null, null, false, null, null, false, false, 0, Map.of(), Map.of(), null,
            null, 1.0, hpPercent, addId, count);
    }

    @Test
    void firesExactlyOnceWhenHpFirstCrossesThreshold() {
        MobInstance mob = new MobInstance(reinforcementTemplate(50, MobId.of("mob.add"), 3));

        assertFalse(mob.tryTriggerReinforcement(), "at full HP the mob is above the threshold");
        assertFalse(mob.hasSummonedReinforcements());

        mob.takeDamage(49); // 100 -> 51, still above 50%
        assertFalse(mob.tryTriggerReinforcement(), "51/100 HP is still above the 50% threshold");

        mob.takeDamage(1); // 51 -> 50, now at the threshold
        assertTrue(mob.tryTriggerReinforcement(), "crossing to 50% HP triggers reinforcements once");
        assertTrue(mob.hasSummonedReinforcements());

        mob.takeDamage(10); // still below the threshold
        assertFalse(mob.tryTriggerReinforcement(),
            "an already-reinforced mob never triggers a second wave this encounter");
        assertTrue(mob.hasSummonedReinforcements());
    }

    @Test
    void nonReinforcementCapableMobNeverTriggers() {
        MobInstance mob = new MobInstance(reinforcementTemplate(null, null, 0));

        mob.takeDamage(99); // 1 HP: well below any conceivable threshold
        assertFalse(mob.tryTriggerReinforcement(),
            "a mob with no reinforcement threshold never summons a wave");
        assertFalse(mob.hasSummonedReinforcements());
    }

    @Test
    void disengageResetsTheReinforcementTrigger() {
        MobInstance mob = new MobInstance(reinforcementTemplate(50, MobId.of("mob.add"), 3));
        Username hero = Username.of("hero");
        mob.engage(hero);

        mob.takeDamage(60); // 100 -> 40, below the threshold
        assertTrue(mob.tryTriggerReinforcement());
        assertTrue(mob.hasSummonedReinforcements());

        mob.disengage(hero);
        assertFalse(mob.hasSummonedReinforcements(),
            "fully disengaging ends the encounter and clears the reinforcement trigger");

        // A fresh pull can trigger the wave again once wounded past the threshold.
        assertTrue(mob.tryTriggerReinforcement(),
            "the re-engaged encounter can summon a fresh wave when still below the threshold");
    }

    @Test
    void respawnResetsReinforcementAndFullHp() {
        MobInstance mob = new MobInstance(reinforcementTemplate(50, MobId.of("mob.add"), 3));
        mob.takeDamage(60);
        assertTrue(mob.tryTriggerReinforcement());

        mob.respawn();
        assertFalse(mob.hasSummonedReinforcements(),
            "a respawned mob starts a fresh, un-triggered encounter at full HP");
        assertFalse(mob.tryTriggerReinforcement(),
            "back at full HP the respawned mob is above the threshold again");
    }
}

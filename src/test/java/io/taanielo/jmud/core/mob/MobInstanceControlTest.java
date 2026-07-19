package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.combat.AttackId;
import io.taanielo.jmud.core.effects.ControlType;
import io.taanielo.jmud.core.world.RoomId;

/**
 * Unit tests for the transient crowd-control lockout on {@link MobInstance} (issue #763): applying a
 * root/silence/stun, its per-AI-decision decrement and expiry, and the reset that fires when the
 * encounter ends (disengage/respawn), mirroring {@code specialAbilityUsed}/enrage/telegraph state.
 */
class MobInstanceControlTest {

    private static final RoomId SPAWN_ROOM = RoomId.of("spawn");
    private static final AttackId ATTACK_ID = AttackId.of("attack.basic");

    private MobTemplate template() {
        return new MobTemplate(
            MobId.of("mob.goblin"), "Goblin", 100, ATTACK_ID, null, true,
            List.of(), SPAWN_ROOM, 1, 10, 5, null, List.of(), false);
    }

    @Test
    void uncontrolledMobReportsNoLockout() {
        MobInstance mob = new MobInstance(template());

        assertNull(mob.activeControl(), "a fresh mob carries no control lockout");
        assertFalse(mob.isStunned());
        assertFalse(mob.isRooted());
        assertFalse(mob.isSilenced());
    }

    @Test
    void applyControlSetsTheMatchingLockout() {
        MobInstance mob = new MobInstance(template());

        mob.applyControl(ControlType.STUN, 3);
        assertSame(ControlType.STUN, mob.activeControl());
        assertTrue(mob.isStunned());
        assertFalse(mob.isRooted());
        assertFalse(mob.isSilenced());
    }

    @Test
    void tickControlDecrementsAndExpiresAfterDuration() {
        MobInstance mob = new MobInstance(template());
        mob.applyControl(ControlType.ROOT, 2);

        assertTrue(mob.isRooted());
        mob.tickControl();
        assertTrue(mob.isRooted(), "still rooted after one of two ticks");
        mob.tickControl();
        assertFalse(mob.isRooted(), "the lockout expires once its duration elapses");
        assertNull(mob.activeControl());
    }

    @Test
    void tickControlIsNoOpWhenUncontrolled() {
        MobInstance mob = new MobInstance(template());

        mob.tickControl();
        assertNull(mob.activeControl(), "ticking an uncontrolled mob does nothing");
    }

    @Test
    void freshControlReplacesAnyPrevious() {
        MobInstance mob = new MobInstance(template());
        mob.applyControl(ControlType.ROOT, 5);

        mob.applyControl(ControlType.SILENCE, 2);
        assertSame(ControlType.SILENCE, mob.activeControl(),
            "the newest lockout wins, replacing the previous control type");
        assertTrue(mob.isSilenced());
        assertFalse(mob.isRooted());
    }

    @Test
    void applyControlRejectsNonPositiveDuration() {
        MobInstance mob = new MobInstance(template());

        assertThrows(IllegalArgumentException.class, () -> mob.applyControl(ControlType.STUN, 0));
        assertThrows(IllegalArgumentException.class, () -> mob.applyControl(ControlType.STUN, -1));
    }

    @Test
    void disengageClearsControlWhenEncounterEnds() {
        MobInstance mob = new MobInstance(template());
        Username hero = Username.of("hero");
        mob.engage(hero);
        mob.applyControl(ControlType.STUN, 5);

        mob.disengage(hero);
        assertNull(mob.activeControl(),
            "fully disengaging ends the encounter and clears control, like specialAbilityUsed");
    }

    @Test
    void respawnClearsControl() {
        MobInstance mob = new MobInstance(template());
        mob.applyControl(ControlType.SILENCE, 5);

        mob.respawn();
        assertNull(mob.activeControl(), "a respawned mob starts uncontrolled");
    }
}

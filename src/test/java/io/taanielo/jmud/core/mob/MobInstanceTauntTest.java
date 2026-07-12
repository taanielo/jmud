package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.world.RoomId;

/**
 * Verifies the taunt-tracking state on {@link MobInstance}: applying a taunt, expiring it after the
 * configured number of AI decisions, and clearing it on taunter disengage and respawn — the same
 * no-dangling-state discipline used for {@code specialAbilityUsed}.
 */
class MobInstanceTauntTest {

    private static final RoomId ROOM_ID = RoomId.of("room.test");
    private static final Username TANK = Username.of("Thane");
    private static final Username ALLY = Username.of("Mira");

    private MobInstance mob() {
        MobTemplate template = new MobTemplate(
            MobId.of("mob.rat"), "Rat", 100, null, null, false, List.of(),
            ROOM_ID, 1, 10, 5, null, null, false);
        return new MobInstance(template);
    }

    @Test
    void activeTaunter_isNullBeforeAnyTaunt() {
        assertNull(mob().activeTaunter());
    }

    @Test
    void applyTaunt_setsActiveTaunter() {
        MobInstance mob = mob();
        mob.applyTaunt(TANK, 3);
        assertEquals(TANK, mob.activeTaunter());
    }

    @Test
    void taunt_expiresAfterConfiguredDecisions() {
        MobInstance mob = mob();
        mob.applyTaunt(TANK, 3);

        mob.consumeTauntTick();
        assertEquals(TANK, mob.activeTaunter(), "Taunt still active after 1 of 3 decisions");
        mob.consumeTauntTick();
        assertEquals(TANK, mob.activeTaunter(), "Taunt still active after 2 of 3 decisions");
        mob.consumeTauntTick();
        assertNull(mob.activeTaunter(), "Taunt expires once all decisions are consumed");
    }

    @Test
    void consumeTauntTick_isNoOpWhenNoTaunt() {
        MobInstance mob = mob();
        mob.consumeTauntTick();
        assertNull(mob.activeTaunter());
    }

    @Test
    void disengagingTaunter_clearsTaunt() {
        MobInstance mob = mob();
        mob.engage(TANK);
        mob.engage(ALLY);
        mob.applyTaunt(TANK, 3);

        mob.disengage(TANK);

        assertNull(mob.activeTaunter(),
            "A taunter leaving combat drops the taunt so the mob resumes normal targeting");
    }

    @Test
    void disengagingOtherPlayer_keepsTaunt() {
        MobInstance mob = mob();
        mob.engage(TANK);
        mob.engage(ALLY);
        mob.applyTaunt(TANK, 3);

        mob.disengage(ALLY);

        assertEquals(TANK, mob.activeTaunter(),
            "An unrelated player disengaging must not drop the tank's taunt");
    }

    @Test
    void respawn_clearsTaunt() {
        MobInstance mob = mob();
        mob.engage(TANK);
        mob.applyTaunt(TANK, 3);

        mob.respawn();

        assertNull(mob.activeTaunter());
    }

    @Test
    void applyTaunt_rejectsNonPositiveDuration() {
        assertThrows(IllegalArgumentException.class, () -> mob().applyTaunt(TANK, 0));
    }
}

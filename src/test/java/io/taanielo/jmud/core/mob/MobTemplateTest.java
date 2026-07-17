package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.TimeOfDay;

/**
 * Unit tests for {@link MobTemplate}'s day/night respawn rate selection.
 */
class MobTemplateTest {

    private static final RoomId SPAWN_ROOM = RoomId.of("spawn");

    private MobTemplate template(int respawnTicks, Integer nightRespawnTicks) {
        return new MobTemplate(
            MobId.of("mob.rat"),
            "Giant Rat",
            20,
            null,
            null,
            false,
            List.of(),
            SPAWN_ROOM,
            1,
            respawnTicks,
            5,
            null,
            List.of(),
            false,
            nightRespawnTicks
        );
    }

    @Test
    void nightRespawnTicksDefaultsToNullViaLegacyConstructor() {
        MobTemplate mobTemplate = new MobTemplate(
            MobId.of("mob.rat"), "Giant Rat", 20, null, null, false,
            List.of(), SPAWN_ROOM, 1, 30, 5, null, List.of(), false);

        assertNull(mobTemplate.nightRespawnTicks());
        assertEquals(30, mobTemplate.respawnTicks(TimeOfDay.DAY));
        assertEquals(30, mobTemplate.respawnTicks(TimeOfDay.NIGHT));
    }

    @Test
    void usesDayRespawnTicksDuringDayEvenWhenNightRespawnTicksConfigured() {
        MobTemplate mobTemplate = template(30, 10);

        assertEquals(30, mobTemplate.respawnTicks(TimeOfDay.DAY));
    }

    @Test
    void usesNightRespawnTicksAtNightWhenConfigured() {
        MobTemplate mobTemplate = template(30, 10);

        assertEquals(10, mobTemplate.respawnTicks(TimeOfDay.NIGHT));
    }

    @Test
    void fallsBackToDayRespawnTicksAtNightWhenNightRespawnTicksNotConfigured() {
        MobTemplate mobTemplate = template(30, null);

        assertEquals(30, mobTemplate.respawnTicks(TimeOfDay.NIGHT));
    }

    @Test
    void rejectsNegativeNightRespawnTicks() {
        assertThrows(IllegalArgumentException.class, () -> template(30, -1));
    }

    @Test
    void parryChanceDefaultsToZeroViaLegacyConstructorSoExistingMobsNeverParry() {
        MobTemplate mobTemplate = template(30, null);

        assertEquals(0, mobTemplate.parryChancePercent(),
            "a template built via the pre-parry constructor defaults to no parry");
        assertFalse(mobTemplate.canParry(), "a zero parry chance means the mob cannot parry");
    }

    @Test
    void canParryReflectsAnAuthoredParryChance() {
        MobTemplate guard = new MobTemplate(
            MobId.of("mob.guard"), "Town Guard", 20, null, null, false,
            List.of(), SPAWN_ROOM, 1, 30, 5, null, List.of(), false, null, null, false,
            null, null, false, false, 20);

        assertEquals(20, guard.parryChancePercent());
        assertTrue(guard.canParry(), "a positive parry chance means the mob can parry");
    }

    @Test
    void rejectsParryChanceAbove100() {
        assertThrows(IllegalArgumentException.class, () -> new MobTemplate(
            MobId.of("mob.guard"), "Town Guard", 20, null, null, false,
            List.of(), SPAWN_ROOM, 1, 30, 5, null, List.of(), false, null, null, false,
            null, null, false, false, 101));
    }
}

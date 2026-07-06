package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.TimeOfDay;

/**
 * Unit tests for {@link MobInstance#scheduleRespawn(TimeOfDay)} and
 * {@link MobInstance#tickRespawn()}, verifying that the day or night respawn delay from
 * {@link MobTemplate} is honored depending on the current {@link TimeOfDay}.
 */
class MobInstanceRespawnTest {

    private static final RoomId SPAWN_ROOM = RoomId.of("spawn");

    private MobTemplate templateWithNightRespawn(int dayTicks, int nightTicks) {
        return new MobTemplate(
            MobId.of("mob.wolf"),
            "Wolf",
            20,
            null,
            null,
            false,
            List.of(),
            SPAWN_ROOM,
            1,
            dayTicks,
            5,
            null,
            List.of(),
            false,
            nightTicks
        );
    }

    @Test
    void respawnsAfterDayRespawnTicksWhenScheduledDuringDay() {
        MobInstance mob = new MobInstance(templateWithNightRespawn(5, 2));
        mob.scheduleRespawn(TimeOfDay.DAY);

        for (int i = 0; i < 4; i++) {
            assertFalse(mob.tickRespawn(), "Should not be ready to respawn before day's respawn ticks elapse");
        }
        assertTrue(mob.tickRespawn(), "Should be ready to respawn once day's respawn ticks elapse");
    }

    @Test
    void respawnsAfterNightRespawnTicksWhenScheduledDuringNight() {
        MobInstance mob = new MobInstance(templateWithNightRespawn(5, 2));
        mob.scheduleRespawn(TimeOfDay.NIGHT);

        assertFalse(mob.tickRespawn(), "Should not be ready to respawn before night's shorter respawn ticks elapse");
        assertTrue(mob.tickRespawn(), "Should be ready to respawn once night's respawn ticks elapse");
    }

    @Test
    void usesDayRespawnTicksAtNightWhenNoNightRespawnTicksConfigured() {
        MobTemplate template = new MobTemplate(
            MobId.of("mob.rat"), "Rat", 20, null, null, false,
            List.of(), SPAWN_ROOM, 1, 3, 5, null, List.of(), false);
        MobInstance mob = new MobInstance(template);
        mob.scheduleRespawn(TimeOfDay.NIGHT);

        assertFalse(mob.tickRespawn());
        assertFalse(mob.tickRespawn());
        assertTrue(mob.tickRespawn());
    }
}

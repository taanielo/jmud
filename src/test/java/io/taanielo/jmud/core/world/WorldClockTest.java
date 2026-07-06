package io.taanielo.jmud.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link WorldClock}'s deterministic, tick-count-driven day/night transitions.
 */
class WorldClockTest {

    @Test
    void startsAtDay() {
        WorldClock clock = new WorldClock(5);

        assertEquals(TimeOfDay.DAY, clock.timeOfDay());
        assertEquals(0, clock.ticksSinceTransition());
    }

    @Test
    void staysDayBeforePhaseLengthElapses() {
        WorldClock clock = new WorldClock(5);

        for (int i = 0; i < 4; i++) {
            clock.tick();
        }

        assertEquals(TimeOfDay.DAY, clock.timeOfDay());
        assertEquals(4, clock.ticksSinceTransition());
    }

    @Test
    void transitionsToNightExactlyAtPhaseLength() {
        WorldClock clock = new WorldClock(5);

        for (int i = 0; i < 5; i++) {
            clock.tick();
        }

        assertEquals(TimeOfDay.NIGHT, clock.timeOfDay());
        assertEquals(0, clock.ticksSinceTransition());
    }

    @Test
    void transitionsBackToDayAfterTwoFullPhases() {
        WorldClock clock = new WorldClock(5);

        for (int i = 0; i < 10; i++) {
            clock.tick();
        }

        assertEquals(TimeOfDay.DAY, clock.timeOfDay());
        assertEquals(0, clock.ticksSinceTransition());
    }

    @Test
    void keepsAlternatingDeterministicallyOverManyPhases() {
        WorldClock clock = new WorldClock(3);
        TimeOfDay[] expected = {
            TimeOfDay.DAY, TimeOfDay.DAY, TimeOfDay.DAY,
            TimeOfDay.NIGHT, TimeOfDay.NIGHT, TimeOfDay.NIGHT,
            TimeOfDay.DAY, TimeOfDay.DAY, TimeOfDay.DAY
        };

        for (TimeOfDay expectedPhase : expected) {
            assertEquals(expectedPhase, clock.timeOfDay());
            clock.tick();
        }
    }

    @Test
    void rejectsNonPositiveTicksPerPhase() {
        assertThrows(IllegalArgumentException.class, () -> new WorldClock(0));
        assertThrows(IllegalArgumentException.class, () -> new WorldClock(-1));
    }

    @Test
    void exposesConfiguredTicksPerPhase() {
        WorldClock clock = new WorldClock(42);

        assertEquals(42, clock.ticksPerPhase());
    }
}

package io.taanielo.jmud.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Room}, focused on the advisory min-level warning check.
 */
class RoomTest {

    private Room roomWithMinLevel(Integer minLevel) {
        return new Room(
            RoomId.of("a"),
            "Room A",
            "A room.",
            Map.of(),
            List.of(),
            List.of(),
            Map.of(),
            minLevel
        );
    }

    @Test
    void hasNoMinLevelByDefault() {
        Room room = new Room(RoomId.of("a"), "Room A", "A room.", Map.of(), List.of(), List.of());

        assertNull(room.getMinLevel());
        assertFalse(room.exceedsLevel(1));
        assertFalse(room.exceedsLevel(100));
    }

    @Test
    void doesNotExceedLevelWhenPlayerLevelIsAtOrAboveMinLevel() {
        Room room = roomWithMinLevel(5);

        assertFalse(room.exceedsLevel(5));
        assertFalse(room.exceedsLevel(6));
    }

    @Test
    void exceedsLevelWhenPlayerLevelIsBelowMinLevel() {
        Room room = roomWithMinLevel(5);

        assertTrue(room.exceedsLevel(4));
        assertTrue(room.exceedsLevel(1));
    }

    @Test
    void hasNoNightDescriptionByDefault() {
        Room room = new Room(RoomId.of("a"), "Room A", "A room.", Map.of(), List.of(), List.of());

        assertNull(room.getNightDescription());
        assertEquals("A room.", room.describeFor(TimeOfDay.DAY));
        assertEquals("A room.", room.describeFor(TimeOfDay.NIGHT));
    }

    @Test
    void describeForReturnsDayDescriptionAtDayEvenWhenNightDescriptionIsDefined() {
        Room room = new Room(
            RoomId.of("a"), "Room A", "A room by day.", Map.of(), List.of(), List.of(),
            Map.of(), null, "A room by night."
        );

        assertEquals("A room by day.", room.describeFor(TimeOfDay.DAY));
    }

    @Test
    void describeForReturnsNightDescriptionAtNightWhenDefined() {
        Room room = new Room(
            RoomId.of("a"), "Room A", "A room by day.", Map.of(), List.of(), List.of(),
            Map.of(), null, "A room by night."
        );

        assertEquals("A room by night.", room.describeFor(TimeOfDay.NIGHT));
    }
}

package io.taanielo.jmud.core.world.area;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.world.RoomId;

class AreaWaypointServiceTest {

    private static Area area(String id, String name, String... roomIds) {
        return new Area(
            AreaId.of(id),
            name,
            List.of(roomIds).stream().map(RoomId::of).toList(),
            List.of(),
            List.of(name + " ART"),
            new LevelRange(1, 5));
    }

    @Test
    void firstRoomOfAreaIsRecognisedAsWaypoint() {
        Area undersong = area("undersong", "The Undersong", "undersong-descent", "undersong-hall");
        AreaWaypointService service = new AreaWaypointService(new StubAreaRepository(List.of(undersong)));

        Optional<Area> found = service.findAreaByWaypoint(RoomId.of("undersong-descent"));

        assertTrue(found.isPresent());
        assertEquals("The Undersong", found.get().name());
        assertTrue(service.isWaypoint(RoomId.of("undersong-descent")));
    }

    @Test
    void nonFirstRoomIsNotAWaypoint() {
        Area undersong = area("undersong", "The Undersong", "undersong-descent", "undersong-hall");
        AreaWaypointService service = new AreaWaypointService(new StubAreaRepository(List.of(undersong)));

        assertFalse(service.isWaypoint(RoomId.of("undersong-hall")));
        assertTrue(service.findAreaByWaypoint(RoomId.of("undersong-hall")).isEmpty());
    }

    @Test
    void unknownRoomIsNotAWaypoint() {
        AreaWaypointService service = new AreaWaypointService(new StubAreaRepository(List.of()));

        assertFalse(service.isWaypoint(RoomId.of("nowhere")));
    }

    private record StubAreaRepository(List<Area> areas) implements AreaRepository {
        @Override
        public List<Area> findAll() {
            return List.copyOf(areas);
        }

        @Override
        public Optional<Area> findById(AreaId id) {
            return areas.stream().filter(a -> a.id().equals(id)).findFirst();
        }

        @Override
        public Optional<WorldAtlas> findAtlas() {
            return Optional.empty();
        }
    }
}

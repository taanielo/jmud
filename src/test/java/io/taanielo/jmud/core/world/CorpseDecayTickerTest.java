package io.taanielo.jmud.core.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.RoomRepository;

/**
 * Unit tests for {@link CorpseDecayTicker} and the corpse-tracking behaviour of
 * {@link RoomService#removeExpiredCorpses(Duration)}.
 */
class CorpseDecayTickerTest {

    private static final RoomId ROOM_ID = RoomId.of("arena");

    /** Builds a RoomService whose starting room is {@code ROOM_ID}. */
    private RoomService buildRoomService() {
        Room room = new Room(ROOM_ID, "Arena", "A sandy arena.", Map.of(), List.of(), List.of());
        return new RoomService(new StubRoomRepository(Map.of(ROOM_ID, room)), ROOM_ID);
    }

    private Username username(String name) {
        return Username.of(name);
    }

    @Test
    void expiredCorpseIsRemovedFromRoomAfterTick() throws InterruptedException {
        RoomService roomService = buildRoomService();
        // 1-nanosecond decay so any corpse spawned before tick is already expired
        CorpseDecayTicker ticker = new CorpseDecayTicker(roomService, Duration.ofNanos(1));

        Username viewer = username("viewer");
        roomService.ensurePlayerLocation(viewer); // places viewer in ROOM_ID

        Username dead = username("ghost");
        roomService.spawnCorpse(dead, ROOM_ID, 42);

        // Verify corpse is initially visible
        List<String> linesBefore = roomService.look(viewer).lines();
        assertTrue(linesBefore.stream().anyMatch(l -> l.contains("ghost")),
            "Corpse should be visible before decay");

        // Sleep 1 ms to ensure the corpse is definitely older than 1 ns
        Thread.sleep(1);
        ticker.tick();

        // Corpse should no longer be visible
        List<String> linesAfter = roomService.look(viewer).lines();
        assertFalse(linesAfter.stream().anyMatch(l -> l.contains("ghost")),
            "Corpse should be removed after decay tick");
    }

    @Test
    void freshCorpseIsRetainedBeforeDecayExpires() {
        RoomService roomService = buildRoomService();
        CorpseDecayTicker ticker = new CorpseDecayTicker(roomService, Duration.ofMinutes(5));

        Username viewer = username("viewer2");
        roomService.ensurePlayerLocation(viewer);

        Username dead = username("brave");
        roomService.spawnCorpse(dead, ROOM_ID, 0);

        ticker.tick();

        // Corpse should still be there (not yet 5 minutes old)
        List<String> linesAfterTick = roomService.look(viewer).lines();
        assertTrue(linesAfterTick.stream().anyMatch(l -> l.contains("brave")),
            "Fresh corpse should remain after a single tick with 5-minute decay");
    }

    @Test
    void constructorRejectsZeroDecayDuration() {
        RoomService roomService = buildRoomService();
        assertThrows(IllegalArgumentException.class,
            () -> new CorpseDecayTicker(roomService, Duration.ZERO));
    }

    @Test
    void constructorRejectsNegativeDecayDuration() {
        RoomService roomService = buildRoomService();
        assertThrows(IllegalArgumentException.class,
            () -> new CorpseDecayTicker(roomService, Duration.ofSeconds(-1)));
    }

    @Test
    void constructorRejectsNullRoomService() {
        assertThrows(NullPointerException.class,
            () -> new CorpseDecayTicker(null, Duration.ofMinutes(5)));
    }

    // ── stubs ─────────────────────────────────────────────────────────

    private record StubRoomRepository(Map<RoomId, Room> rooms) implements RoomRepository {
        private StubRoomRepository(Map<RoomId, Room> rooms) {
            this.rooms = new ConcurrentHashMap<>(rooms);
        }

        @Override
        public void save(Room room) throws RepositoryException {
            rooms.put(room.getId(), room);
        }

        @Override
        public Optional<Room> findById(RoomId id) throws RepositoryException {
            return Optional.ofNullable(rooms.get(id));
        }
    }
}

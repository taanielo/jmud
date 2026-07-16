package io.taanielo.jmud.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.RoomRepository;
import io.taanielo.jmud.core.world.repository.json.JsonDiscoveredExitsRepository;

/**
 * Integration tests at the repository + service level proving that hidden-exit discovery survives a
 * server restart: an exit found via SEARCH stays open for a freshly-connecting player after the
 * store is reloaded, while never-found exits stay hidden.
 */
class PlayerLocationServicePersistenceTest {

    private static final RoomId ROOM_A = RoomId.of("a");
    private static final RoomId ROOM_B = RoomId.of("b");
    private static final Path STORE = Path.of("world-state", "discovered-exits.json");

    private static Map<RoomId, Room> worldWithHiddenExit() {
        Room roomA = new Room(ROOM_A, "Room A", "A room.", Map.of(), List.of(), List.of(),
            Map.of(), null, null, null, false, List.of(), Map.of(Direction.DOWN, ROOM_B));
        Room roomB = new Room(ROOM_B, "Room B", "A room.", Map.of(Direction.UP, ROOM_A),
            List.of(), List.of());
        return Map.of(ROOM_A, roomA, ROOM_B, roomB);
    }

    @Test
    void discoveredHiddenExitStaysOpenForNewPlayerAfterRestart(@TempDir Path dataRoot) {
        Map<RoomId, Room> world = worldWithHiddenExit();
        RoomRepository roomRepository = new TestRoomRepository(world);

        // First "server run": Alice searches the hidden exit open.
        JsonDiscoveredExitsRepository store = new JsonDiscoveredExitsRepository(dataRoot);
        PlayerLocationService service = new PlayerLocationService(roomRepository, ROOM_A, store);
        Username alice = Username.of("Alice");
        service.ensurePlayerLocation(alice);
        assertFalse(service.revealHiddenExits(alice).isEmpty(), "Search should reveal the hidden exit");
        waitForFile(dataRoot.resolve(STORE), true);
        store.close();

        // "Restart": rebuild the store and service from disk, connect a fresh player.
        JsonDiscoveredExitsRepository reloadedStore = new JsonDiscoveredExitsRepository(dataRoot);
        PlayerLocationService restarted =
            new PlayerLocationService(roomRepository, ROOM_A, reloadedStore);
        try {
            Username bob = Username.of("Bob");
            restarted.ensurePlayerLocation(bob);
            assertTrue(restarted.getVisibleExits(ROOM_A).containsKey(Direction.DOWN),
                "Previously-discovered exit must be visible again after restart");
            assertTrue(restarted.undiscoveredHiddenExits(bob).isEmpty(),
                "Nothing left to discover for a fresh player after restart");
            assertInstanceOf(PlayerLocationService.MoveAttempt.Succeeded.class,
                restarted.attemptMove(bob, Direction.DOWN));
            assertEquals(Optional.of(ROOM_B), restarted.findPlayerLocation(bob));
        } finally {
            reloadedStore.close();
        }
    }

    @Test
    void undiscoveredHiddenExitStaysHiddenAfterReloadOfMalformedStore(@TempDir Path dataRoot)
            throws Exception {
        Path dir = Files.createDirectories(dataRoot.resolve("world-state"));
        Files.writeString(dir.resolve("discovered-exits.json"), "}} not json {{");
        RoomRepository roomRepository = new TestRoomRepository(worldWithHiddenExit());

        JsonDiscoveredExitsRepository store = new JsonDiscoveredExitsRepository(dataRoot);
        PlayerLocationService service = new PlayerLocationService(roomRepository, ROOM_A, store);
        try {
            Username alice = Username.of("Alice");
            service.ensurePlayerLocation(alice);
            assertFalse(service.getVisibleExits(ROOM_A).containsKey(Direction.DOWN),
                "A malformed store must not reveal any exit");
            assertTrue(service.undiscoveredHiddenExits(alice).contains(Direction.DOWN));
            assertInstanceOf(PlayerLocationService.MoveAttempt.Failed.class,
                service.attemptMove(alice, Direction.DOWN));
        } finally {
            store.close();
        }
    }

    private static void waitForFile(Path path, boolean shouldExist) {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            if (Files.exists(path) == shouldExist) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        assertTrue(Files.exists(path) == shouldExist,
            "Timed out waiting for file " + path + " exists=" + shouldExist);
    }

    private record TestRoomRepository(Map<RoomId, Room> rooms) implements RoomRepository {
        TestRoomRepository(Map<RoomId, Room> rooms) {
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

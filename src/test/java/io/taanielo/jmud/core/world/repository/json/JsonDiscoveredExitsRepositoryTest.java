package io.taanielo.jmud.core.world.repository.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.taanielo.jmud.core.world.Direction;
import io.taanielo.jmud.core.world.RoomId;

/**
 * Unit tests for {@link JsonDiscoveredExitsRepository} covering write-behind persistence round-trips,
 * absent-file defaults, and defensive loading of malformed or partial stores.
 */
class JsonDiscoveredExitsRepositoryTest {

    private static final RoomId ROOM = RoomId.of("crypt-antechamber");
    private static final Path FILE = Path.of("world-state", "discovered-exits.json");

    @Test
    void savesAndReloadsDiscoveryRoundTrip(@TempDir Path dataRoot) {
        JsonDiscoveredExitsRepository repository = new JsonDiscoveredExitsRepository(dataRoot);
        try {
            repository.save(ROOM, Set.of(Direction.DOWN, Direction.NORTH));
            waitForFile(dataRoot.resolve(FILE), true);
        } finally {
            repository.close();
        }

        JsonDiscoveredExitsRepository reloaded = new JsonDiscoveredExitsRepository(dataRoot);
        try {
            Map<RoomId, Set<Direction>> all = reloaded.loadAll();
            assertEquals(Set.of(Direction.DOWN, Direction.NORTH), all.get(ROOM));
        } finally {
            reloaded.close();
        }
    }

    @Test
    void missingStoreLoadsAsEmpty(@TempDir Path dataRoot) {
        JsonDiscoveredExitsRepository repository = new JsonDiscoveredExitsRepository(dataRoot);
        try {
            assertTrue(repository.loadAll().isEmpty(),
                "A world with no discoveries yet must reveal nothing");
        } finally {
            repository.close();
        }
    }

    @Test
    void malformedStoreLoadsAsEmptyWithoutOverReveal(@TempDir Path dataRoot) throws Exception {
        Path dir = Files.createDirectories(dataRoot.resolve("world-state"));
        Files.writeString(dir.resolve("discovered-exits.json"), "{ this is not valid json ][");

        JsonDiscoveredExitsRepository repository = new JsonDiscoveredExitsRepository(dataRoot);
        try {
            assertTrue(repository.loadAll().isEmpty(),
                "A corrupt store must never accidentally reveal an exit");
        } finally {
            repository.close();
        }
    }

    @Test
    void partialStoreSkipsUnknownDirectionsAndBlankRooms(@TempDir Path dataRoot) throws Exception {
        Path dir = Files.createDirectories(dataRoot.resolve("world-state"));
        Files.writeString(dir.resolve("discovered-exits.json"), """
            {
              "schema_version": 1,
              "rooms": [
                { "room_id": "crypt-antechamber", "directions": ["DOWN", "SIDEWAYS"] },
                { "room_id": "", "directions": ["NORTH"] }
              ]
            }
            """);

        JsonDiscoveredExitsRepository repository = new JsonDiscoveredExitsRepository(dataRoot);
        try {
            Map<RoomId, Set<Direction>> all = repository.loadAll();
            assertEquals(Set.of(Direction.DOWN), all.get(ROOM),
                "Unknown direction must be dropped, valid one kept");
            assertEquals(1, all.size(),
                "Only the one well-formed room should load; the blank-id entry must be skipped");
        } finally {
            repository.close();
        }
    }

    @Test
    void unsupportedSchemaVersionLoadsAsEmpty(@TempDir Path dataRoot) throws Exception {
        Path dir = Files.createDirectories(dataRoot.resolve("world-state"));
        Files.writeString(dir.resolve("discovered-exits.json"), """
            {
              "schema_version": 99,
              "rooms": [
                { "room_id": "crypt-antechamber", "directions": ["DOWN"] }
              ]
            }
            """);

        JsonDiscoveredExitsRepository repository = new JsonDiscoveredExitsRepository(dataRoot);
        try {
            assertTrue(repository.loadAll().isEmpty(),
                "A store written by a future schema must not be partially trusted");
        } finally {
            repository.close();
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
}

package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomService;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.RoomRepository;

/**
 * Unit tests for the wizard-facing {@link MobRegistry#spawnInstance} and {@link MobRegistry#purgeMob}
 * methods backing the SPAWN and PURGE admin commands.
 */
class MobRegistryAdminTest {

    private static final RoomId SPAWN_ROOM = RoomId.of("spawn");
    private static final RoomId OTHER_ROOM = RoomId.of("other");

    private MobTemplate goblin() {
        return new MobTemplate(
            MobId.of("goblin"), "Goblin", 20, null, null, false, List.of(),
            SPAWN_ROOM, 1, 10, 5, null, List.of(), false);
    }

    private RoomService roomService() {
        Room spawn = new Room(SPAWN_ROOM, "Spawn", "A room.", Map.of(), List.of(), List.of());
        Room other = new Room(OTHER_ROOM, "Other", "A room.", Map.of(), List.of(), List.of());
        RoomRepository repo = new StubRoomRepository(Map.of(SPAWN_ROOM, spawn, OTHER_ROOM, other));
        return new RoomService(repo, SPAWN_ROOM);
    }

    private MobRegistry registry() {
        return MobRegistryTestFactory.create(roomService(), List.of(goblin()));
    }

    @Test
    void spawnInstance_createsMobInTargetRoom() {
        MobRegistry registry = registry();
        assertTrue(registry.getMobsInRoom(OTHER_ROOM).isEmpty());

        Optional<MobInstance> spawned = registry.spawnInstance(MobId.of("goblin"), OTHER_ROOM);

        assertTrue(spawned.isPresent());
        assertEquals(OTHER_ROOM, spawned.get().roomId());
        assertEquals(1, registry.getMobsInRoom(OTHER_ROOM).size());
    }

    @Test
    void spawnInstance_returnsEmptyForUnknownTemplate() {
        assertTrue(registry().spawnInstance(MobId.of("dragon"), OTHER_ROOM).isEmpty());
    }

    @Test
    void purgeMob_removesMatchingMob() {
        MobRegistry registry = registry();
        assertEquals(1, registry.getMobsInRoom(SPAWN_ROOM).size());

        Optional<String> purged = registry.purgeMob(SPAWN_ROOM, "goblin");

        assertTrue(purged.isPresent());
        assertEquals("Goblin", purged.get());
        assertTrue(registry.getMobsInRoom(SPAWN_ROOM).isEmpty());
    }

    @Test
    void purgeMob_returnsEmptyWhenNoMatch() {
        MobRegistry registry = registry();
        assertTrue(registry.purgeMob(SPAWN_ROOM, "orc").isEmpty());
        assertTrue(registry.purgeMob(SPAWN_ROOM, "  ").isEmpty());
        assertFalse(registry.getMobsInRoom(SPAWN_ROOM).isEmpty(), "no match must not remove any mob");
    }

    private record StubRoomRepository(Map<RoomId, Room> rooms) implements RoomRepository {
        @Override public void save(Room room) throws RepositoryException {}

        @Override public Optional<Room> findById(RoomId id) throws RepositoryException {
            return Optional.ofNullable(rooms.get(id));
        }
    }
}

package io.taanielo.jmud.core.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.world.Direction;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomService;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.RoomRepository;

class PlayerRespawnTickerTest {

    @Test
    void respawnsAfterConfiguredTicks() {
        RoomId startId = RoomId.of("start");
        RoomId arenaId = RoomId.of("arena");
        Room start = new Room(
            startId,
            "Start",
            "A quiet place.",
            Map.of(),
            List.of(),
            List.of()
        );
        Room arena = new Room(
            arenaId,
            "Arena",
            "An empty arena.",
            Map.of(Direction.SOUTH, startId),
            List.of(new Item(ItemId.of("stone"), "Stone", "A small stone.", ItemAttributes.empty(), List.of(), 0)),
            List.of()
        );
        RoomService roomService = new RoomService(new TestRoomRepository(Map.of(startId, start, arenaId, arena)), startId);

        PlayerVitals vitals = new PlayerVitals(10, 20, 10, 20, 10, 20);
        Player player = new Player(
            User.of(Username.of("sparky"), Password.hash("pw", 1000)),
            1,
            0,
            vitals,
            List.of(),
            "prompt",
            false,
            List.of(),
            null,
            null
        ).die();
        AtomicReference<Player> ref = new AtomicReference<>(player);
        PlayerRespawnTicker ticker = new PlayerRespawnTicker(ref::get, ref::set, roomService, 2);

        ticker.schedule();
        ticker.tick();
        assertTrue(ref.get().isDead());

        ticker.tick();
        Player respawned = ref.get();
        assertFalse(respawned.isDead());
        assertEquals(10, respawned.getVitals().hp());
        assertEquals(Optional.of(startId), roomService.findPlayerLocation(respawned.getUsername()));
    }

    private record TestRoomRepository(Map<RoomId, Room> rooms) implements RoomRepository {
        private TestRoomRepository(Map<RoomId, Room> rooms) {
            this.rooms = new ConcurrentHashMap<>(rooms);
        }

        @Override
        public void save(Room room) throws RepositoryException {
            if (room == null) {
                throw new RepositoryException("Room is required");
            }
            rooms.put(room.getId(), room);
        }

        @Override
        public Optional<Room> findById(RoomId id) throws RepositoryException {
            if (id == null) {
                throw new RepositoryException("Room id is required");
            }
            return Optional.ofNullable(rooms.get(id));
        }
    }
}

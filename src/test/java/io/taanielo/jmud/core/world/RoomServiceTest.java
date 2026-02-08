package io.taanielo.jmud.core.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.RoomRepository;

class RoomServiceTest {

    @Test
    void movesBetweenRoomsUsingExits() {
        RoomId roomAId = RoomId.of("a");
        RoomId roomBId = RoomId.of("b");
        Room roomA = new Room(
            roomAId,
            "Room A",
            "A quiet room.",
            Map.of(Direction.NORTH, roomBId),
            List.of(),
            List.of()
        );
        Room roomB = new Room(
            roomBId,
            "Room B",
            "A bright room.",
            Map.of(Direction.SOUTH, roomAId),
            List.of(),
            List.of()
        );
        RoomService service = new RoomService(new TestRoomRepository(Map.of(roomAId, roomA, roomBId, roomB)), roomAId);

        Username player = Username.of("Bob");
        service.ensurePlayerLocation(player);
        RoomService.MoveResult moveResult = service.move(player, Direction.NORTH);

        assertTrue(moveResult.moved());
        assertTrue(moveResult.lines().getFirst().contains("move north"));
    }

    @Test
    void rejectsInvalidMove() {
        RoomId roomAId = RoomId.of("a");
        Room roomA = new Room(
            roomAId,
            "Room A",
            "A quiet room.",
            Map.of(),
            List.of(),
            List.of()
        );
        RoomService service = new RoomService(new TestRoomRepository(Map.of(roomAId, roomA)), roomAId);

        Username player = Username.of("Bob");
        service.ensurePlayerLocation(player);
        RoomService.MoveResult moveResult = service.move(player, Direction.WEST);

        assertFalse(moveResult.moved());
        assertTrue(moveResult.lines().getFirst().contains("cannot go west"));
    }

    @Test
    void lookShowsExitsItemsAndOccupants() {
        RoomId roomAId = RoomId.of("a");
        Item item = new Item(
            ItemId.of("torch"),
            "Torch",
            "A warm torch.",
            ItemAttributes.empty(),
            List.of(),
            List.of(),
            null,
            1,
            5
        );
        Room roomA = new Room(
            roomAId,
            "Room A",
            "A quiet room.",
            Map.of(Direction.EAST, RoomId.of("b")),
            List.of(item),
            List.of()
        );
        RoomService service = new RoomService(new TestRoomRepository(Map.of(roomAId, roomA)), roomAId);

        Username bob = Username.of("Bob");
        Username alice = Username.of("Alice");
        service.ensurePlayerLocation(bob);
        service.ensurePlayerLocation(alice);

        RoomService.LookResult lookResult = service.look(bob);
        String output = String.join("\n", lookResult.lines());

        assertTrue(output.contains("Exits: east"));
        assertTrue(output.contains("Items: Torch"));
        assertTrue(output.contains("Occupants: Alice"));
    }

    @Test
    void spawnCorpseAddsItemToRoom() {
        RoomId roomAId = RoomId.of("a");
        Room roomA = new Room(
            roomAId,
            "Room A",
            "A quiet room.",
            Map.of(),
            List.of(),
            List.of()
        );
        RoomService service = new RoomService(new TestRoomRepository(Map.of(roomAId, roomA)), roomAId);

        Username victim = Username.of("Bob");
        service.ensurePlayerLocation(victim);
        service.spawnCorpse(victim, roomAId);

        RoomService.LookResult lookResult = service.look(Username.of("Alice"));
        String output = String.join("\n", lookResult.lines());

        assertTrue(output.contains("Corpse of Bob"));
    }

    @Test
    void takeItemRemovesFromRoom() {
        RoomId roomAId = RoomId.of("a");
        Item torch = new Item(
            ItemId.of("torch"),
            "Torch",
            "A warm torch.",
            ItemAttributes.empty(),
            List.of(),
            List.of(),
            null,
            1,
            5
        );
        Room roomA = new Room(
            roomAId,
            "Room A",
            "A quiet room.",
            Map.of(),
            List.of(torch),
            List.of()
        );
        RoomService service = new RoomService(new TestRoomRepository(Map.of(roomAId, roomA)), roomAId);

        Username bob = Username.of("Bob");
        service.ensurePlayerLocation(bob);
        assertTrue(service.takeItem(bob, "torch").isPresent());

        RoomService.LookResult lookResult = service.look(bob);
        String output = String.join("\n", lookResult.lines());

        assertTrue(output.contains("Items: none"));
    }

    @Test
    void dropItemAddsToRoom() {
        RoomId roomAId = RoomId.of("a");
        Room roomA = new Room(
            roomAId,
            "Room A",
            "A quiet room.",
            Map.of(),
            List.of(),
            List.of()
        );
        RoomService service = new RoomService(new TestRoomRepository(Map.of(roomAId, roomA)), roomAId);

        Username bob = Username.of("Bob");
        service.ensurePlayerLocation(bob);
        Item apple = new Item(
            ItemId.of("apple"),
            "Apple",
            "A crisp apple.",
            ItemAttributes.empty(),
            List.of(),
            List.of(),
            null,
            1,
            1
        );
        service.dropItem(bob, apple);

        RoomService.LookResult lookResult = service.look(bob);
        String output = String.join("\n", lookResult.lines());

        assertTrue(output.contains("Apple"));
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

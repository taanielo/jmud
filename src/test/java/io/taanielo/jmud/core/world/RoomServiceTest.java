package io.taanielo.jmud.core.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.output.PlainTextStyler;
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
        assertTrue(moveResult.lines().stream().anyMatch(l -> l.equals("A bright room.")),
            "Default (full) move should include the destination's prose description");
    }

    @Test
    void briefModeMoveOmitsDestinationDescription() {
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
        RoomService.MoveResult moveResult = service.move(
            player, Direction.NORTH, new PlainTextStyler(), RoomRenderer.DescriptionMode.BRIEF);

        assertTrue(moveResult.moved());
        assertTrue(moveResult.lines().stream().anyMatch(l -> l.equals("Room B")),
            "Brief move should still show the destination room name");
        assertFalse(moveResult.lines().stream().anyMatch(l -> l.equals("A bright room.")),
            "Brief move should omit the destination's prose description");
    }

    @Test
    void movePreservesDestinationRoomMinLevel() {
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
            "A dangerous room.",
            Map.of(Direction.SOUTH, roomAId),
            List.of(),
            List.of(),
            Map.of(),
            5
        );
        RoomService service = new RoomService(new TestRoomRepository(Map.of(roomAId, roomA, roomBId, roomB)), roomAId);

        Username player = Username.of("Bob");
        service.ensurePlayerLocation(player);
        RoomService.MoveResult moveResult = service.move(player, Direction.NORTH);

        assertTrue(moveResult.moved());
        assertTrue(moveResult.room() != null && moveResult.room().exceedsLevel(4));
        assertFalse(moveResult.room() != null && moveResult.room().exceedsLevel(5));
    }

    @Test
    void lookShowsDayDescriptionWhenNoWorldClockIsRegistered() {
        RoomId roomAId = RoomId.of("a");
        Room roomA = new Room(
            roomAId, "Room A", "A quiet room by day.", Map.of(), List.of(), List.of(),
            Map.of(), null, "A dark room by night."
        );
        RoomService service = new RoomService(new TestRoomRepository(Map.of(roomAId, roomA)), roomAId);

        Username player = Username.of("Bob");
        service.ensurePlayerLocation(player);
        RoomService.LookResult lookResult = service.look(player);

        assertTrue(lookResult.lines().get(1).equals("A quiet room by day."));
    }

    @Test
    void lookShowsDayDescriptionWhenWorldClockIsDay() {
        RoomId roomAId = RoomId.of("a");
        Room roomA = new Room(
            roomAId, "Room A", "A quiet room by day.", Map.of(), List.of(), List.of(),
            Map.of(), null, "A dark room by night."
        );
        RoomService service = new RoomService(new TestRoomRepository(Map.of(roomAId, roomA)), roomAId);
        service.setWorldClock(new WorldClock(100));

        Username player = Username.of("Bob");
        service.ensurePlayerLocation(player);
        RoomService.LookResult lookResult = service.look(player);

        assertTrue(lookResult.lines().get(1).equals("A quiet room by day."));
    }

    @Test
    void lookShowsNightDescriptionWhenWorldClockIsNight() {
        RoomId roomAId = RoomId.of("a");
        Room roomA = new Room(
            roomAId, "Room A", "A quiet room by day.", Map.of(), List.of(), List.of(),
            Map.of(), null, "A dark room by night."
        );
        RoomService service = new RoomService(new TestRoomRepository(Map.of(roomAId, roomA)), roomAId);
        WorldClock worldClock = new WorldClock(1);
        worldClock.tick(); // flips to NIGHT after 1 tick
        service.setWorldClock(worldClock);

        Username player = Username.of("Bob");
        service.ensurePlayerLocation(player);
        RoomService.LookResult lookResult = service.look(player);

        assertTrue(lookResult.lines().get(1).equals("A dark room by night."));
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
        Item item = Item.builder(
            ItemId.of("torch"), "Torch", "A warm torch.", ItemAttributes.empty())
            .weight(1)
            .value(5)
            .build();
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
        service.spawnCorpse(victim, roomAId, 0);

        RoomService.LookResult lookResult = service.look(Username.of("Alice"));
        String output = String.join("\n", lookResult.lines());

        assertTrue(output.contains("corpse of Bob"));
    }

    @Test
    void takeItemRemovesFromRoom() {
        RoomId roomAId = RoomId.of("a");
        Item torch = Item.builder(
            ItemId.of("torch"), "Torch", "A warm torch.", ItemAttributes.empty())
            .weight(1)
            .value(5)
            .build();
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
        Item apple = Item.builder(ItemId.of("apple"), "Apple", "A crisp apple.", ItemAttributes.empty())
            .weight(1)
            .value(1)
            .build();
        service.dropItem(bob, apple);

        RoomService.LookResult lookResult = service.look(bob);
        String output = String.join("\n", lookResult.lines());

        assertTrue(output.contains("Apple"));
    }

    // ── Lock / Unlock tests ─────────────────────────────────────────────

    @Test
    void moveBlockedThroughLockedExit() {
        RoomId roomAId = RoomId.of("a");
        RoomId roomBId = RoomId.of("b");
        ItemId keyId = ItemId.of("iron-key");
        Room roomA = new Room(
            roomAId,
            "Room A",
            "A quiet room.",
            Map.of(Direction.NORTH, roomBId),
            List.of(),
            List.of(),
            Map.of(Direction.NORTH, keyId)
        );
        Room roomB = new Room(
            roomBId,
            "Room B",
            "A bright room.",
            Map.of(Direction.SOUTH, roomAId),
            List.of(),
            List.of(),
            Map.of(Direction.SOUTH, keyId)
        );
        RoomService service = new RoomService(new TestRoomRepository(Map.of(roomAId, roomA, roomBId, roomB)), roomAId);
        Username player = Username.of("Bob");
        service.ensurePlayerLocation(player);

        RoomService.MoveResult result = service.move(player, Direction.NORTH);

        assertFalse(result.moved());
        assertTrue(result.lines().getFirst().contains("locked"));
    }

    @Test
    void unlockWithCorrectKeyAllowsMovement() {
        RoomId roomAId = RoomId.of("a");
        RoomId roomBId = RoomId.of("b");
        ItemId keyId = ItemId.of("iron-key");
        Item key = Item.builder(keyId, "Iron Key", "A key.", ItemAttributes.empty())
            .weight(1)
            .value(0)
            .build();
        Room roomA = new Room(
            roomAId, "Room A", "A quiet room.",
            Map.of(Direction.NORTH, roomBId), List.of(), List.of(),
            Map.of(Direction.NORTH, keyId)
        );
        Room roomB = new Room(
            roomBId, "Room B", "A bright room.",
            Map.of(Direction.SOUTH, roomAId), List.of(), List.of(),
            Map.of(Direction.SOUTH, keyId)
        );
        RoomService service = new RoomService(new TestRoomRepository(Map.of(roomAId, roomA, roomBId, roomB)), roomAId);
        Username player = Username.of("Bob");
        service.ensurePlayerLocation(player);

        DoorActionResult unlockResult = service.unlock(player, Direction.NORTH, List.of(key));
        assertTrue(unlockResult.success());
        assertTrue(unlockResult.playerMessage().contains("unlock"));

        RoomService.MoveResult moveResult = service.move(player, Direction.NORTH);
        assertTrue(moveResult.moved());
    }

    @Test
    void unlockWithoutKeyFails() {
        RoomId roomAId = RoomId.of("a");
        RoomId roomBId = RoomId.of("b");
        ItemId keyId = ItemId.of("iron-key");
        Room roomA = new Room(
            roomAId, "Room A", "A quiet room.",
            Map.of(Direction.NORTH, roomBId), List.of(), List.of(),
            Map.of(Direction.NORTH, keyId)
        );
        Room roomB = new Room(
            roomBId, "Room B", "A bright room.",
            Map.of(Direction.SOUTH, roomAId), List.of(), List.of()
        );
        RoomService service = new RoomService(new TestRoomRepository(Map.of(roomAId, roomA, roomBId, roomB)), roomAId);
        Username player = Username.of("Bob");
        service.ensurePlayerLocation(player);

        DoorActionResult result = service.unlock(player, Direction.NORTH, List.of());

        assertFalse(result.success());
        assertTrue(result.playerMessage().contains("key"));
    }

    @Test
    void lockAfterUnlockRestoresBlockedMovement() {
        RoomId roomAId = RoomId.of("a");
        RoomId roomBId = RoomId.of("b");
        ItemId keyId = ItemId.of("iron-key");
        Item key = Item.builder(keyId, "Iron Key", "A key.", ItemAttributes.empty())
            .weight(1)
            .value(0)
            .build();
        Room roomA = new Room(
            roomAId, "Room A", "A quiet room.",
            Map.of(Direction.NORTH, roomBId), List.of(), List.of(),
            Map.of(Direction.NORTH, keyId)
        );
        Room roomB = new Room(
            roomBId, "Room B", "A bright room.",
            Map.of(Direction.SOUTH, roomAId), List.of(), List.of(),
            Map.of(Direction.SOUTH, keyId)
        );
        RoomService service = new RoomService(new TestRoomRepository(Map.of(roomAId, roomA, roomBId, roomB)), roomAId);
        Username player = Username.of("Bob");
        service.ensurePlayerLocation(player);

        // Unlock then lock again
        assertTrue(service.unlock(player, Direction.NORTH, List.of(key)).success());
        assertTrue(service.lock(player, Direction.NORTH, List.of(key)).success());

        // Should be blocked again
        assertFalse(service.move(player, Direction.NORTH).moved());
    }

    @Test
    void lookShowsLockedSuffix() {
        RoomId roomAId = RoomId.of("a");
        RoomId roomBId = RoomId.of("b");
        ItemId keyId = ItemId.of("iron-key");
        Room roomA = new Room(
            roomAId, "Room A", "A quiet room.",
            Map.of(Direction.NORTH, roomBId), List.of(), List.of(),
            Map.of(Direction.NORTH, keyId)
        );
        Room roomB = new Room(
            roomBId, "Room B", "A bright room.",
            Map.of(Direction.SOUTH, roomAId), List.of(), List.of()
        );
        RoomService service = new RoomService(new TestRoomRepository(Map.of(roomAId, roomA, roomBId, roomB)), roomAId);
        Username player = Username.of("Bob");
        service.ensurePlayerLocation(player);

        RoomService.LookResult look = service.look(player);
        String output = String.join("\n", look.lines());

        assertTrue(output.contains("[locked]"), "Expected '[locked]' in exits line, got: " + output);
    }

    @Test
    void unlockFromOneSideUnlocksBothSides() {
        RoomId roomAId = RoomId.of("a");
        RoomId roomBId = RoomId.of("b");
        ItemId keyId = ItemId.of("iron-key");
        Item key = Item.builder(keyId, "Iron Key", "A key.", ItemAttributes.empty())
            .weight(1)
            .value(0)
            .build();
        Room roomA = new Room(
            roomAId, "Room A", "A quiet room.",
            Map.of(Direction.NORTH, roomBId), List.of(), List.of(),
            Map.of(Direction.NORTH, keyId)
        );
        Room roomB = new Room(
            roomBId, "Room B", "A bright room.",
            Map.of(Direction.SOUTH, roomAId), List.of(), List.of(),
            Map.of(Direction.SOUTH, keyId)
        );
        RoomService service = new RoomService(new TestRoomRepository(Map.of(roomAId, roomA, roomBId, roomB)), roomAId);
        Username playerA = Username.of("Alice");
        Username playerB = Username.of("Bob");
        service.ensurePlayerLocation(playerA); // Alice in room A
        // Move Bob to room B first using direct location assignment
        service.ensurePlayerLocation(playerB);
        // Manually place Bob in room B by unlocking and moving
        service.unlock(playerA, Direction.NORTH, List.of(key));
        // Alice unlocked from room A; now Bob (also in room A) tries to move north
        // First re-lock so we can verify Bob in B after Alice unlocks
        service.lock(playerA, Direction.NORTH, List.of(key));

        // Alice unlocks from room A side
        assertTrue(service.unlock(playerA, Direction.NORTH, List.of(key)).success());

        // Move Bob to room B
        service.move(playerA, Direction.NORTH);
        // Now Alice is in room B; simulate Bob being in room A at the start
        // Let's simplify: verify that the south exit from room B is also unlocked
        // Re-place Alice back in room A for clarity
        service.respawnPlayer(playerA); // puts Alice in starting room = roomA
        service.move(playerA, Direction.NORTH); // Alice goes north (unlocked)
        // Alice is now in room B - the reverse (south) should also be unlocked
        RoomService.MoveResult backResult = service.move(playerA, Direction.SOUTH);
        assertTrue(backResult.moved(), "Reverse direction should be unlocked after unlocking from other side");
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

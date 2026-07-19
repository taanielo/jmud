package io.taanielo.jmud.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.RoomRepository;

/**
 * Unit tests for {@link PlayerLocationService}: move, lock, and unlock behaviour.
 */
class PlayerLocationServiceTest {

    private static final RoomId ROOM_A = RoomId.of("a");
    private static final RoomId ROOM_B = RoomId.of("b");
    private static final ItemId KEY_ID = ItemId.of("iron-key");
    private static final Item KEY = Item.builder(KEY_ID, "Iron Key", "A key.", ItemAttributes.empty())
        .weight(1)
        .value(0)
        .build();

    private static Room basicRoom(RoomId id, Map<Direction, RoomId> exits) {
        return new Room(id, "Room " + id.getValue(), "A room.", exits, List.of(), List.of());
    }

    private static Room lockedRoom(RoomId id, Map<Direction, RoomId> exits,
            Map<Direction, ItemId> lockedExits) {
        return new Room(id, "Room " + id.getValue(), "A room.", exits, List.of(), List.of(),
            lockedExits);
    }

    private static Room hiddenRoom(RoomId id, Map<Direction, RoomId> exits,
            Map<Direction, RoomId> hiddenExits) {
        return hiddenRoom(id, exits, hiddenExits, Map.of());
    }

    private static Room hiddenRoom(RoomId id, Map<Direction, RoomId> exits,
            Map<Direction, RoomId> hiddenExits, Map<Direction, ItemId> lockedExits) {
        return new Room(id, "Room " + id.getValue(), "A room.", exits, List.of(), List.of(),
            lockedExits, null, null, null, false, List.of(), hiddenExits);
    }

    private PlayerLocationService buildService(Map<RoomId, Room> rooms) {
        return new PlayerLocationService(new TestRoomRepository(rooms), ROOM_A);
    }

    // ── Location tracking ─────────────────────────────────────────────

    @Test
    void ensurePlayerLocationAssignsStartingRoom() {
        PlayerLocationService service = buildService(Map.of(
            ROOM_A, basicRoom(ROOM_A, Map.of())));
        Username alice = Username.of("Alice");

        RoomId location = service.ensurePlayerLocation(alice);

        assertEquals(ROOM_A, location);
    }

    @Test
    void clearPlayerLocationRemovesEntry() {
        PlayerLocationService service = buildService(Map.of(
            ROOM_A, basicRoom(ROOM_A, Map.of())));
        Username alice = Username.of("Alice");
        service.ensurePlayerLocation(alice);

        service.clearPlayerLocation(alice);

        assertTrue(service.findPlayerLocation(alice).isEmpty());
    }

    @Test
    void respawnPlayerSetsStartingRoom() {
        PlayerLocationService service = buildService(Map.of(
            ROOM_A, basicRoom(ROOM_A, Map.of(Direction.NORTH, ROOM_B)),
            ROOM_B, basicRoom(ROOM_B, Map.of(Direction.SOUTH, ROOM_A))));
        Username alice = Username.of("Alice");
        service.ensurePlayerLocation(alice);
        // Move to room B
        service.attemptMove(alice, Direction.NORTH);
        assertEquals(Optional.of(ROOM_B), service.findPlayerLocation(alice));

        service.respawnPlayer(alice);

        assertEquals(Optional.of(ROOM_A), service.findPlayerLocation(alice));
    }

    @Test
    void respawnPlayerHonoursResolvablePreferredRoom() {
        PlayerLocationService service = buildService(Map.of(
            ROOM_A, basicRoom(ROOM_A, Map.of(Direction.NORTH, ROOM_B)),
            ROOM_B, basicRoom(ROOM_B, Map.of(Direction.SOUTH, ROOM_A))));
        Username alice = Username.of("Alice");
        service.ensurePlayerLocation(alice);

        RoomId landed = service.respawnPlayer(alice, ROOM_B);

        assertEquals(ROOM_B, landed);
        assertEquals(Optional.of(ROOM_B), service.findPlayerLocation(alice));
    }

    @Test
    void respawnPlayerFallsBackToStartWhenPreferredRoomMissing() {
        PlayerLocationService service = buildService(Map.of(
            ROOM_A, basicRoom(ROOM_A, Map.of(Direction.NORTH, ROOM_B)),
            ROOM_B, basicRoom(ROOM_B, Map.of(Direction.SOUTH, ROOM_A))));
        Username alice = Username.of("Alice");
        service.ensurePlayerLocation(alice);
        service.attemptMove(alice, Direction.NORTH);

        RoomId landed = service.respawnPlayer(alice, RoomId.of("does-not-exist"));

        assertEquals(ROOM_A, landed);
        assertEquals(Optional.of(ROOM_A), service.findPlayerLocation(alice));
    }

    @Test
    void movePlayerToRelocatesDirectlyBypassingExitChecks() {
        PlayerLocationService service = buildService(Map.of(
            ROOM_A, basicRoom(ROOM_A, Map.of()),
            ROOM_B, basicRoom(ROOM_B, Map.of())));
        Username alice = Username.of("Alice");
        service.ensurePlayerLocation(alice);

        // Room A has no exit to Room B, yet a fiat move still succeeds (e.g. a ferry arrival).
        service.movePlayerTo(alice, ROOM_B);

        assertEquals(Optional.of(ROOM_B), service.findPlayerLocation(alice));
        assertEquals(List.of(alice), service.getPlayersInRoom(ROOM_B));
    }

    @Test
    void getPlayersInRoomReturnsOccupants() {
        PlayerLocationService service = buildService(Map.of(
            ROOM_A, basicRoom(ROOM_A, Map.of())));
        Username alice = Username.of("Alice");
        Username bob = Username.of("Bob");
        service.ensurePlayerLocation(alice);
        service.ensurePlayerLocation(bob);

        List<Username> occupants = service.getPlayersInRoom(ROOM_A);

        assertTrue(occupants.contains(alice));
        assertTrue(occupants.contains(bob));
    }

    // ── Movement ──────────────────────────────────────────────────────

    @Test
    void attemptMoveSucceedsWhenExitExists() {
        PlayerLocationService service = buildService(Map.of(
            ROOM_A, basicRoom(ROOM_A, Map.of(Direction.NORTH, ROOM_B)),
            ROOM_B, basicRoom(ROOM_B, Map.of(Direction.SOUTH, ROOM_A))));
        Username alice = Username.of("Alice");
        service.ensurePlayerLocation(alice);

        PlayerLocationService.MoveAttempt result = service.attemptMove(alice, Direction.NORTH);

        assertInstanceOf(PlayerLocationService.MoveAttempt.Succeeded.class, result);
        assertEquals(Optional.of(ROOM_B), service.findPlayerLocation(alice));
    }

    @Test
    void attemptMoveFailsWhenNoExit() {
        PlayerLocationService service = buildService(Map.of(
            ROOM_A, basicRoom(ROOM_A, Map.of())));
        Username alice = Username.of("Alice");
        service.ensurePlayerLocation(alice);

        PlayerLocationService.MoveAttempt result = service.attemptMove(alice, Direction.WEST);

        assertInstanceOf(PlayerLocationService.MoveAttempt.Failed.class, result);
        assertEquals(Optional.of(ROOM_A), service.findPlayerLocation(alice),
            "Player should still be in room A after failed move");
    }

    @Test
    void attemptMoveFailsThroughLockedExit() {
        PlayerLocationService service = buildService(Map.of(
            ROOM_A, lockedRoom(ROOM_A, Map.of(Direction.NORTH, ROOM_B),
                Map.of(Direction.NORTH, KEY_ID)),
            ROOM_B, basicRoom(ROOM_B, Map.of(Direction.SOUTH, ROOM_A))));
        Username alice = Username.of("Alice");
        service.ensurePlayerLocation(alice);

        PlayerLocationService.MoveAttempt result = service.attemptMove(alice, Direction.NORTH);

        PlayerLocationService.MoveAttempt.Failed failed =
            assertInstanceOf(PlayerLocationService.MoveAttempt.Failed.class, result);
        assertTrue(failed.reason().contains("locked"), "Reason should mention 'locked'");
    }

    // ── Hidden exits ────────────────────────────────────────────────────

    @Test
    void undiscoveredHiddenExitBlocksMovementAndIsInvisible() {
        PlayerLocationService service = buildService(Map.of(
            ROOM_A, hiddenRoom(ROOM_A, Map.of(), Map.of(Direction.DOWN, ROOM_B)),
            ROOM_B, basicRoom(ROOM_B, Map.of(Direction.UP, ROOM_A))));
        Username alice = Username.of("Alice");
        service.ensurePlayerLocation(alice);

        assertFalse(service.getVisibleExits(ROOM_A).containsKey(Direction.DOWN),
            "Undiscovered hidden exit must not appear in the visible exit map");
        assertTrue(service.undiscoveredHiddenExits(alice).contains(Direction.DOWN));
        assertInstanceOf(PlayerLocationService.MoveAttempt.Failed.class,
            service.attemptMove(alice, Direction.DOWN));
        assertEquals(Optional.of(ROOM_A), service.findPlayerLocation(alice));
    }

    @Test
    void revealedHiddenExitBecomesVisibleAndWalkable() {
        PlayerLocationService service = buildService(Map.of(
            ROOM_A, hiddenRoom(ROOM_A, Map.of(), Map.of(Direction.DOWN, ROOM_B)),
            ROOM_B, basicRoom(ROOM_B, Map.of(Direction.UP, ROOM_A))));
        Username alice = Username.of("Alice");
        service.ensurePlayerLocation(alice);

        assertEquals(Set.of(Direction.DOWN), service.revealHiddenExits(alice));
        assertTrue(service.getVisibleExits(ROOM_A).containsKey(Direction.DOWN),
            "Revealed hidden exit should appear in the visible exit map");
        assertTrue(service.undiscoveredHiddenExits(alice).isEmpty(),
            "Nothing left to discover after revealing the only hidden exit");
        assertInstanceOf(PlayerLocationService.MoveAttempt.Succeeded.class,
            service.attemptMove(alice, Direction.DOWN));
        assertEquals(Optional.of(ROOM_B), service.findPlayerLocation(alice));
    }

    @Test
    void discoveryIsWorldScopedAcrossPlayers() {
        PlayerLocationService service = buildService(Map.of(
            ROOM_A, hiddenRoom(ROOM_A, Map.of(), Map.of(Direction.DOWN, ROOM_B)),
            ROOM_B, basicRoom(ROOM_B, Map.of(Direction.UP, ROOM_A))));
        Username alice = Username.of("Alice");
        Username bob = Username.of("Bob");
        service.ensurePlayerLocation(alice);
        service.ensurePlayerLocation(bob);

        service.revealHiddenExits(alice);

        // Bob, who never searched, still benefits from Alice's discovery.
        assertTrue(service.undiscoveredHiddenExits(bob).isEmpty());
        assertInstanceOf(PlayerLocationService.MoveAttempt.Succeeded.class,
            service.attemptMove(bob, Direction.DOWN));
    }

    @Test
    void hiddenExitThatIsAlsoLockedStaysLockedAfterDiscovery() {
        PlayerLocationService service = buildService(Map.of(
            ROOM_A, hiddenRoom(ROOM_A, Map.of(), Map.of(Direction.DOWN, ROOM_B),
                Map.of(Direction.DOWN, KEY_ID)),
            ROOM_B, basicRoom(ROOM_B, Map.of(Direction.UP, ROOM_A))));
        Username alice = Username.of("Alice");
        service.ensurePlayerLocation(alice);

        service.revealHiddenExits(alice);

        PlayerLocationService.MoveAttempt result = service.attemptMove(alice, Direction.DOWN);
        PlayerLocationService.MoveAttempt.Failed failed =
            assertInstanceOf(PlayerLocationService.MoveAttempt.Failed.class, result);
        assertTrue(failed.reason().contains("locked"),
            "Discovery reveals the exit but must not unlock it");
    }

    // ── Lock / Unlock ──────────────────────────────────────────────────

    @Test
    void unlockWithCorrectKeySucceeds() {
        PlayerLocationService service = buildService(Map.of(
            ROOM_A, lockedRoom(ROOM_A, Map.of(Direction.NORTH, ROOM_B),
                Map.of(Direction.NORTH, KEY_ID)),
            ROOM_B, basicRoom(ROOM_B, Map.of(Direction.SOUTH, ROOM_A))));
        Username alice = Username.of("Alice");
        service.ensurePlayerLocation(alice);

        DoorActionResult result = service.unlock(alice, Direction.NORTH, List.of(KEY));

        assertTrue(result.success());
        assertFalse(service.isExitLocked(ROOM_A, Direction.NORTH),
            "Exit should be unlocked after successful unlock");
    }

    @Test
    void unlockWithoutKeyFails() {
        PlayerLocationService service = buildService(Map.of(
            ROOM_A, lockedRoom(ROOM_A, Map.of(Direction.NORTH, ROOM_B),
                Map.of(Direction.NORTH, KEY_ID)),
            ROOM_B, basicRoom(ROOM_B, Map.of(Direction.SOUTH, ROOM_A))));
        Username alice = Username.of("Alice");
        service.ensurePlayerLocation(alice);

        DoorActionResult result = service.unlock(alice, Direction.NORTH, List.of());

        assertFalse(result.success());
        assertTrue(service.isExitLocked(ROOM_A, Direction.NORTH),
            "Exit should remain locked after failed unlock");
    }

    @Test
    void lockAfterUnlockBlocksMovement() {
        PlayerLocationService service = buildService(Map.of(
            ROOM_A, lockedRoom(ROOM_A, Map.of(Direction.NORTH, ROOM_B),
                Map.of(Direction.NORTH, KEY_ID)),
            ROOM_B, basicRoom(ROOM_B, Map.of(Direction.SOUTH, ROOM_A))));
        Username alice = Username.of("Alice");
        service.ensurePlayerLocation(alice);

        service.unlock(alice, Direction.NORTH, List.of(KEY));
        service.lock(alice, Direction.NORTH, List.of(KEY));

        assertInstanceOf(PlayerLocationService.MoveAttempt.Failed.class,
            service.attemptMove(alice, Direction.NORTH));
    }

    @Test
    void unlockFromOneSidePropagatesReverse() {
        Room roomA = lockedRoom(ROOM_A, Map.of(Direction.NORTH, ROOM_B),
            Map.of(Direction.NORTH, KEY_ID));
        Room roomB = lockedRoom(ROOM_B, Map.of(Direction.SOUTH, ROOM_A),
            Map.of(Direction.SOUTH, KEY_ID));
        PlayerLocationService service = buildService(Map.of(ROOM_A, roomA, ROOM_B, roomB));
        Username alice = Username.of("Alice");
        service.ensurePlayerLocation(alice);

        service.unlock(alice, Direction.NORTH, List.of(KEY));

        assertFalse(service.isExitLocked(ROOM_A, Direction.NORTH),
            "North exit of room A should be unlocked");
        assertFalse(service.isExitLocked(ROOM_B, Direction.SOUTH),
            "South exit of room B should also be unlocked (propagated)");
    }

    // ── stubs ──────────────────────────────────────────────────────────

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

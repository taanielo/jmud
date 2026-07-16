package io.taanielo.jmud.core.world;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.RoomRepository;

/**
 * Domain service that owns the player-location map, movement validation, and door lock state.
 *
 * <p>Movement includes exit existence checks, locked-door enforcement, and bi-directional lock
 * propagation. Lock state is seeded lazily from room data and maintained in memory for the
 * duration of the server session.
 *
 * <p>All mutation happens on the tick thread (AGENTS.md §5).
 */
public class PlayerLocationService {

    private final RoomRepository roomRepository;
    private final RoomId startingRoomId;
    private final ConcurrentHashMap<Username, RoomId> playerLocations = new ConcurrentHashMap<>();
    /**
     * Runtime locked-exit state: maps each room id to the set of directions that are currently
     * locked. Seeded lazily from each room's {@link Room#getLockedExits()} on first access.
     */
    private final ConcurrentHashMap<RoomId, Set<Direction>> runtimeLockedExits = new ConcurrentHashMap<>();
    /**
     * Runtime discovered-hidden-exit state: maps each room id to the set of secret exit directions
     * that have been found (via SEARCH). Discovery is world-scoped: once found, a hidden exit is
     * visible and walkable for every player. The set starts empty for each room — all hidden exits
     * begin undiscovered — is seeded at construction from {@link #discoveredExitsRepository}, and is
     * persisted back on each new discovery so found exits survive a server restart.
     */
    private final ConcurrentHashMap<RoomId, Set<Direction>> runtimeDiscoveredHiddenExits = new ConcurrentHashMap<>();
    private final DiscoveredExitsRepository discoveredExitsRepository;

    /**
     * Creates a player location service without durable hidden-exit discovery.
     *
     * <p>Discoveries are kept only for the current server session (they reset on restart). Prefer
     * {@link #PlayerLocationService(RoomRepository, RoomId, DiscoveredExitsRepository)} in production
     * so found exits are persisted.
     *
     * @param roomRepository the room data store used to validate exits and key requirements
     * @param startingRoomId the room that new or respawning players are placed into
     */
    public PlayerLocationService(RoomRepository roomRepository, RoomId startingRoomId) {
        this(roomRepository, startingRoomId, DiscoveredExitsRepository.noOp());
    }

    /**
     * Creates a player location service backed by a persisted hidden-exit discovery store.
     *
     * <p>Previously-discovered hidden exits are loaded eagerly from the repository so they come back
     * open immediately for every player after a restart, honouring SEARCH's permanence promise.
     *
     * @param roomRepository            the room data store used to validate exits and key requirements
     * @param startingRoomId            the room that new or respawning players are placed into
     * @param discoveredExitsRepository the store that persists world-scoped hidden-exit discoveries
     */
    public PlayerLocationService(
        RoomRepository roomRepository,
        RoomId startingRoomId,
        DiscoveredExitsRepository discoveredExitsRepository) {
        this.roomRepository = Objects.requireNonNull(roomRepository, "Room repository is required");
        this.startingRoomId = Objects.requireNonNull(startingRoomId, "Starting room id is required");
        this.discoveredExitsRepository =
            Objects.requireNonNull(discoveredExitsRepository, "Discovered exits repository is required");
        discoveredExitsRepository.loadAll().forEach((roomId, directions) -> {
            Set<Direction> set = ConcurrentHashMap.newKeySet();
            set.addAll(directions);
            runtimeDiscoveredHiddenExits.put(roomId, set);
        });
    }

    /**
     * Returns all player usernames currently in the given room.
     *
     * @param roomId the room to query
     * @return an unmodifiable list of occupant usernames
     */
    public List<Username> getPlayersInRoom(RoomId roomId) {
        Objects.requireNonNull(roomId, "Room id is required");
        return playerLocations.entrySet().stream()
            .filter(e -> e.getValue().equals(roomId))
            .map(Map.Entry::getKey)
            .toList();
    }

    /**
     * Returns the set of room ids that currently contain at least one player.
     *
     * <p>Used by world tickers (e.g. ambient flavour emission) that only need to act on occupied
     * rooms, so empty rooms are skipped entirely.
     *
     * @return an unmodifiable set of occupied room ids (may be empty, never {@code null})
     */
    public Set<RoomId> occupiedRooms() {
        return Set.copyOf(playerLocations.values());
    }

    /**
     * Ensures the player has a location, defaulting to the starting room if absent.
     *
     * @param username the player
     * @return the player's current (or newly assigned) room id
     */
    public RoomId ensurePlayerLocation(Username username) {
        Objects.requireNonNull(username, "Username is required");
        return playerLocations.computeIfAbsent(username, ignored -> startingRoomId);
    }

    /**
     * Returns the player's current room id, if tracked.
     *
     * @param username the player
     * @return the room id, or empty if the player has no location
     */
    public Optional<RoomId> findPlayerLocation(Username username) {
        Objects.requireNonNull(username, "Username is required");
        return Optional.ofNullable(playerLocations.get(username));
    }

    /**
     * Removes the player from the location map (used on death or disconnect).
     *
     * @param username the player to remove
     */
    public void clearPlayerLocation(Username username) {
        Objects.requireNonNull(username, "Username is required");
        playerLocations.remove(username);
    }

    /**
     * Moves the player to the starting (respawn) room.
     *
     * @param username the player to respawn
     * @return the starting room id
     */
    public RoomId respawnPlayer(Username username) {
        Objects.requireNonNull(username, "Username is required");
        playerLocations.put(username, startingRoomId);
        return startingRoomId;
    }

    /**
     * Relocates the player directly to the given room, bypassing exit and lock checks.
     *
     * <p>Used by world systems that move players by fiat rather than by a chosen direction — for
     * example a scheduled ferry carrying its deck passengers to the next dock. Must only be called
     * from the tick thread (AGENTS.md §5).
     *
     * @param username the player to relocate
     * @param roomId   the destination room id
     */
    public void movePlayerTo(Username username, RoomId roomId) {
        Objects.requireNonNull(username, "Username is required");
        Objects.requireNonNull(roomId, "Room id is required");
        playerLocations.put(username, roomId);
    }

    /**
     * Returns the exit map for the given room, or an empty map if the room cannot be found.
     *
     * <p>Used by mob AI to enumerate candidate wander destinations.
     *
     * @param roomId the room to query
     * @return an unmodifiable map of direction to neighbouring room id
     */
    public Map<Direction, RoomId> getExits(RoomId roomId) {
        Objects.requireNonNull(roomId, "Room id is required");
        return findRoom(roomId).map(Room::getExits).orElse(Map.of());
    }

    /**
     * Returns the set of currently locked exit directions for the given room.
     *
     * <p>The set is lazily seeded from the room's static locked-exit configuration on first call.
     *
     * @param roomId the room to query
     * @return the mutable set of locked directions (may be empty, never null)
     */
    public Set<Direction> getLockedExits(RoomId roomId) {
        Objects.requireNonNull(roomId, "Room id is required");
        return ensureLockedState(roomId);
    }

    /**
     * Returns {@code true} if the exit in the given direction is currently locked.
     *
     * @param roomId    the room containing the exit
     * @param direction the exit direction to test
     * @return {@code true} if locked
     */
    public boolean isExitLocked(RoomId roomId, Direction direction) {
        Objects.requireNonNull(roomId, "Room id is required");
        Objects.requireNonNull(direction, "Direction is required");
        return ensureLockedState(roomId).contains(direction);
    }

    /**
     * Returns the set of hidden exit directions in the given room that have been discovered this
     * session. The set is lazily created (initially empty) on first call, mirroring the lifecycle of
     * {@link #getLockedExits(RoomId)} but seeded to "all undiscovered" rather than from room data.
     *
     * @param roomId the room to query
     * @return the mutable set of discovered hidden-exit directions (may be empty, never null)
     */
    public Set<Direction> getDiscoveredHiddenExits(RoomId roomId) {
        Objects.requireNonNull(roomId, "Room id is required");
        return ensureDiscoveredState(roomId);
    }

    /**
     * Returns the effective visible exit map for the given room: its normal exits plus any hidden
     * exits that have already been discovered. Undiscovered hidden exits are excluded so callers
     * (e.g. the room renderer) never expose them.
     *
     * @param roomId the room to query
     * @return an unmodifiable map of direction to destination room id for all currently visible exits
     */
    public Map<Direction, RoomId> getVisibleExits(RoomId roomId) {
        Objects.requireNonNull(roomId, "Room id is required");
        Room room = findRoom(roomId).orElse(null);
        if (room == null) {
            return Map.of();
        }
        if (!room.hasHiddenExits()) {
            return room.getExits();
        }
        Map<Direction, RoomId> visible = new LinkedHashMap<>(room.getExits());
        Set<Direction> discovered = ensureDiscoveredState(roomId);
        room.getHiddenExits().forEach((direction, destination) -> {
            if (discovered.contains(direction)) {
                visible.put(direction, destination);
            }
        });
        return Map.copyOf(visible);
    }

    /**
     * Returns the hidden exit directions in the player's current room that have not yet been
     * discovered.
     *
     * @param username the player whose room is inspected
     * @return an unmodifiable set of undiscovered hidden-exit directions (may be empty, never null)
     */
    public Set<Direction> undiscoveredHiddenExits(Username username) {
        Objects.requireNonNull(username, "Username is required");
        Room room = loadRoomForPlayer(username).orElse(null);
        if (room == null || !room.hasHiddenExits()) {
            return Set.of();
        }
        Set<Direction> discovered = ensureDiscoveredState(room.getId());
        return room.getHiddenExits().keySet().stream()
            .filter(direction -> !discovered.contains(direction))
            .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Marks every currently-undiscovered hidden exit in the player's current room as discovered,
     * making them visible and walkable for all players from now on.
     *
     * <p>Discovery only reveals an exit; it never unlocks one. A hidden exit that is also locked
     * remains locked after being found. Must run on the tick thread (AGENTS.md §5).
     *
     * @param username the player performing the discovery
     * @return the set of directions newly revealed by this call (may be empty, never null)
     */
    public Set<Direction> revealHiddenExits(Username username) {
        Objects.requireNonNull(username, "Username is required");
        Room room = loadRoomForPlayer(username).orElse(null);
        if (room == null || !room.hasHiddenExits()) {
            return Set.of();
        }
        Set<Direction> discovered = ensureDiscoveredState(room.getId());
        Set<Direction> revealed = new HashSet<>();
        for (Direction direction : room.getHiddenExits().keySet()) {
            if (discovered.add(direction)) {
                revealed.add(direction);
            }
        }
        if (!revealed.isEmpty()) {
            // Persist only on the rare discovery event, never on a per-move/per-tick path.
            discoveredExitsRepository.save(room.getId(), Set.copyOf(discovered));
        }
        return Set.copyOf(revealed);
    }

    /**
     * Returns the room currently occupied by the player, loading it from the repository.
     *
     * <p>Ensures the player has a location (assigns starting room if absent) before resolving.
     *
     * @param username the player
     * @return the loaded room, or empty if not found
     */
    public Optional<Room> loadRoomForPlayer(Username username) {
        Objects.requireNonNull(username, "Username is required");
        RoomId roomId = ensurePlayerLocation(username);
        return findRoom(roomId);
    }

    /**
     * Attempts to move the player in the given direction.
     *
     * <p>On success, updates {@code playerLocations} and returns the destination room. On failure,
     * returns the reason message and the player's current room (may be {@code null} if the world
     * has no rooms).
     *
     * @param username  the player to move
     * @param direction the direction to move in
     * @return a {@link MoveAttempt} describing the outcome
     */
    public MoveAttempt attemptMove(Username username, Direction direction) {
        Objects.requireNonNull(username, "Username is required");
        Objects.requireNonNull(direction, "Direction is required");
        Room room = loadRoomForPlayer(username).orElse(null);
        if (room == null) {
            return new MoveAttempt.Failed("You cannot move yet. The world has no rooms.", null);
        }
        RoomId destinationId = room.getExits().get(direction);
        if (destinationId == null) {
            // A hidden exit becomes walkable only once discovered; an undiscovered one is
            // indistinguishable from a wall, so it falls through to the same failure below.
            RoomId hiddenDest = room.getHiddenExits().get(direction);
            if (hiddenDest != null && ensureDiscoveredState(room.getId()).contains(direction)) {
                destinationId = hiddenDest;
            }
        }
        if (destinationId == null) {
            return new MoveAttempt.Failed("You cannot go " + direction.label() + ".", room);
        }
        if (isExitLocked(room.getId(), direction)) {
            return new MoveAttempt.Failed("The door to the " + direction.label() + " is locked.", room);
        }
        Room destination = findRoom(destinationId).orElse(null);
        if (destination == null) {
            return new MoveAttempt.Failed("The way " + direction.label() + " is blocked.", room);
        }
        playerLocations.put(username, destinationId);
        return new MoveAttempt.Succeeded(destination);
    }

    /**
     * The outcome of a {@link #attemptMove(Username, Direction)} call.
     */
    public sealed interface MoveAttempt permits MoveAttempt.Failed, MoveAttempt.Succeeded {

        /**
         * Movement was not possible; {@link #currentRoom()} is the room the player remains in
         * (may be {@code null} if the world has no rooms at all).
         */
        record Failed(String reason, @Nullable Room currentRoom) implements MoveAttempt {}

        /** Movement succeeded; the player is now in {@link #destination()}. */
        record Succeeded(Room destination) implements MoveAttempt {}
    }

    /**
     * Attempts to unlock the exit in the given direction from the player's current room.
     *
     * <p>Succeeds when the exit is declared lockable in room data, is currently locked, and the
     * player's inventory contains the required key item. On success, bi-directionally propagates
     * the state change to the destination room's reverse exit (if also lockable).
     *
     * @param username  the acting player
     * @param direction the direction of the exit to unlock
     * @param inventory the player's current inventory (used to check for the required key)
     * @return the result of the action
     */
    public DoorActionResult unlock(Username username, Direction direction, List<Item> inventory) {
        Objects.requireNonNull(username, "Username is required");
        Objects.requireNonNull(direction, "Direction is required");
        Objects.requireNonNull(inventory, "Inventory is required");
        Room room = loadRoomForPlayer(username).orElse(null);
        if (room == null) {
            return new DoorActionResult(false, "You are nowhere.", null);
        }
        ItemId requiredKey = room.getLockedExits().get(direction);
        if (requiredKey == null) {
            return new DoorActionResult(false, "There is no lockable door to the " + direction.label() + ".", null);
        }
        Set<Direction> locked = ensureLockedState(room.getId());
        if (!locked.contains(direction)) {
            return new DoorActionResult(false, "The door to the " + direction.label() + " is already unlocked.", null);
        }
        if (!hasItem(inventory, requiredKey)) {
            return new DoorActionResult(false, "You need the right key to unlock this door.", null);
        }
        locked.remove(direction);
        propagateLockState(room, direction, false);
        String playerMsg = "You unlock the door to the " + direction.label() + ".";
        String roomMsg = username.getValue() + " unlocks the door to the " + direction.label() + ".";
        return new DoorActionResult(true, playerMsg, roomMsg);
    }

    /**
     * Attempts to lock the exit in the given direction from the player's current room.
     *
     * <p>Succeeds when the exit is declared lockable, is currently unlocked, and the player's
     * inventory contains the required key item. On success, bi-directionally propagates the state
     * change to the destination room's reverse exit (if also lockable).
     *
     * @param username  the acting player
     * @param direction the direction of the exit to lock
     * @param inventory the player's current inventory
     * @return the result of the action
     */
    public DoorActionResult lock(Username username, Direction direction, List<Item> inventory) {
        Objects.requireNonNull(username, "Username is required");
        Objects.requireNonNull(direction, "Direction is required");
        Objects.requireNonNull(inventory, "Inventory is required");
        Room room = loadRoomForPlayer(username).orElse(null);
        if (room == null) {
            return new DoorActionResult(false, "You are nowhere.", null);
        }
        ItemId requiredKey = room.getLockedExits().get(direction);
        if (requiredKey == null) {
            return new DoorActionResult(false, "There is no lockable door to the " + direction.label() + ".", null);
        }
        Set<Direction> locked = ensureLockedState(room.getId());
        if (locked.contains(direction)) {
            return new DoorActionResult(false, "The door to the " + direction.label() + " is already locked.", null);
        }
        if (!hasItem(inventory, requiredKey)) {
            return new DoorActionResult(false, "You need the right key to lock this door.", null);
        }
        locked.add(direction);
        propagateLockState(room, direction, true);
        String playerMsg = "You lock the door to the " + direction.label() + ".";
        String roomMsg = username.getValue() + " locks the door to the " + direction.label() + ".";
        return new DoorActionResult(true, playerMsg, roomMsg);
    }

    private Set<Direction> ensureLockedState(RoomId roomId) {
        return runtimeLockedExits.computeIfAbsent(roomId, id -> {
            Set<Direction> set = ConcurrentHashMap.newKeySet();
            findRoom(id).ifPresent(r -> set.addAll(r.getLockedExits().keySet()));
            return set;
        });
    }

    private Set<Direction> ensureDiscoveredState(RoomId roomId) {
        return runtimeDiscoveredHiddenExits.computeIfAbsent(roomId, id -> ConcurrentHashMap.newKeySet());
    }

    private void propagateLockState(Room room, Direction direction, boolean nowLocked) {
        RoomId destId = room.getExits().get(direction);
        if (destId == null) {
            return;
        }
        Room dest = findRoom(destId).orElse(null);
        if (dest == null) {
            return;
        }
        Direction reverse = direction.opposite();
        if (!dest.getLockedExits().containsKey(reverse)) {
            return;
        }
        Set<Direction> destLocked = ensureLockedState(destId);
        if (nowLocked) {
            destLocked.add(reverse);
        } else {
            destLocked.remove(reverse);
        }
    }

    private boolean hasItem(List<Item> inventory, ItemId itemId) {
        for (Item item : inventory) {
            if (item.getId().equals(itemId)) {
                return true;
            }
        }
        return false;
    }

    private Optional<Room> findRoom(RoomId roomId) {
        try {
            return roomRepository.findById(roomId);
        } catch (RepositoryException e) {
            return Optional.empty();
        }
    }
}

package io.taanielo.jmud.core.world;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.RoomRepository;

/**
 * Domain service for resolving player room state, movement, and room descriptions.
 */
public class RoomService {

    /**
     * Result of a look action, including rendered lines and the resolved room.
     */
    public record LookResult(List<String> lines, Room room) {
    }

    /**
     * Result of a move action, including whether movement succeeded, output lines, and the resolved room.
     */
    public record MoveResult(boolean moved, List<String> lines, Room room) {
    }

    /**
     * Result of a lock or unlock door action.
     *
     * @param success       whether the action was performed
     * @param playerMessage message shown to the acting player
     * @param roomMessage   message broadcast to other room occupants, or {@code null} on failure
     */
    public record DoorActionResult(boolean success, String playerMessage, String roomMessage) {
    }

    private final RoomRepository roomRepository;
    private final RoomId startingRoomId;
    private final ConcurrentHashMap<Username, RoomId> playerLocations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<RoomId, List<Item>> transientItems = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Corpse> trackedCorpses = new ConcurrentLinkedQueue<>();
    private final AtomicLong corpseCounter = new AtomicLong();
    /**
     * Runtime locked-exit state: maps each room id to the set of directions that are currently locked.
     * Seeded lazily from each room's {@link Room#getLockedExits()} on first access.
     */
    private final ConcurrentHashMap<RoomId, Set<Direction>> runtimeLockedExits = new ConcurrentHashMap<>();

    /**
     * Creates a room service with the provided repository and starting room id.
     */
    public RoomService(RoomRepository roomRepository, RoomId startingRoomId) {
        this.roomRepository = Objects.requireNonNull(roomRepository, "Room repository is required");
        this.startingRoomId = Objects.requireNonNull(startingRoomId, "Starting room id is required");
    }

    /**
     * Returns all player usernames currently in the given room.
     */
    public List<Username> getPlayersInRoom(RoomId roomId) {
        Objects.requireNonNull(roomId, "Room id is required");
        return playerLocations.entrySet().stream()
            .filter(e -> e.getValue().equals(roomId))
            .map(java.util.Map.Entry::getKey)
            .toList();
    }

    /**
     * Ensures a player has a location, defaulting to the starting room if missing.
     */
    public RoomId ensurePlayerLocation(Username username) {
        Objects.requireNonNull(username, "Username is required");
        return playerLocations.computeIfAbsent(username, ignored -> startingRoomId);
    }

    /**
     * Returns the current player location, if any.
     */
    public Optional<RoomId> findPlayerLocation(Username username) {
        Objects.requireNonNull(username, "Username is required");
        return Optional.ofNullable(playerLocations.get(username));
    }

    /**
     * Removes a player from the room tracking map.
     */
    public void clearPlayerLocation(Username username) {
        Objects.requireNonNull(username, "Username is required");
        playerLocations.remove(username);
    }

    /**
     * Respawns a player at the starting room.
     */
    public RoomId respawnPlayer(Username username) {
        Objects.requireNonNull(username, "Username is required");
        playerLocations.put(username, startingRoomId);
        return startingRoomId;
    }

    /**
     * Returns the exit map for the given room, or an empty map if the room cannot be found.
     *
     * <p>Used by mob AI to enumerate candidate wander destinations.
     *
     * @param roomId the room to query
     * @return an unmodifiable map of direction to neighbouring room id (never null)
     */
    public Map<Direction, RoomId> getExits(RoomId roomId) {
        Objects.requireNonNull(roomId, "Room id is required");
        return findRoom(roomId).map(Room::getExits).orElse(Map.of());
    }

    /**
     * Adds a transient item to the specified room.
     */
    public void addItem(RoomId roomId, Item item) {
        Objects.requireNonNull(roomId, "Room id is required");
        Objects.requireNonNull(item, "Item is required");
        transientItems.compute(roomId, (id, existing) -> {
            List<Item> items = new ArrayList<>(existing == null ? List.of() : existing);
            items.add(item);
            return List.copyOf(items);
        });
    }

    /**
     * Spawns a corpse item in the specified room, carrying the given amount of gold.
     *
     * <p>The spawned corpse is tracked internally and will be removed after the
     * configured decay period by {@link #removeExpiredCorpses(Duration)}.
     *
     * @param username the player who died
     * @param roomId   the room where the corpse is placed
     * @param gold     gold carried by the player at time of death (0 if none)
     * @return the {@link Corpse} tracking record
     */
    public Corpse spawnCorpse(Username username, RoomId roomId, int gold) {
        Objects.requireNonNull(username, "Username is required");
        Objects.requireNonNull(roomId, "Room id is required");
        if (gold < 0) {
            throw new IllegalArgumentException("Gold must be non-negative");
        }
        Item corpseItem = createCorpse(username, gold);
        addItem(roomId, corpseItem);
        Corpse corpse = new Corpse(corpseItem.getId(), roomId, username.getValue(), gold, Instant.now());
        trackedCorpses.add(corpse);
        return corpse;
    }

    /**
     * Removes all tracked corpses that were spawned longer ago than {@code decayAfter}.
     *
     * <p>Called by {@link CorpseDecayTicker} on each tick.
     *
     * @param decayAfter the maximum age a corpse may be before it is removed
     */
    public void removeExpiredCorpses(Duration decayAfter) {
        Objects.requireNonNull(decayAfter, "Decay duration is required");
        Instant cutoff = Instant.now().minus(decayAfter);
        trackedCorpses.removeIf(corpse -> {
            if (!corpse.spawnedAt().isAfter(cutoff)) {
                removeTransientItemById(corpse.roomId(), corpse.itemId());
                return true;
            }
            return false;
        });
    }

    /**
     * Removes an item from the player's current room.
     */
    public Optional<Item> takeItem(Username username, String input) {
        Objects.requireNonNull(username, "Username is required");
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        Room room = loadRoomForPlayer(username);
        if (room == null) {
            return Optional.empty();
        }
        Item match = matchItem(room.getItems(), input);
        if (match != null) {
            removeRoomItem(room, match);
            return Optional.of(match);
        }
        List<Item> extras = transientItems.get(room.getId());
        if (extras == null || extras.isEmpty()) {
            return Optional.empty();
        }
        match = matchItem(extras, input);
        if (match == null) {
            return Optional.empty();
        }
        removeTransientItem(room.getId(), match);
        return Optional.of(match);
    }

    /**
     * Drops an item into the player's current room.
     */
    public boolean dropItem(Username username, Item item) {
        Objects.requireNonNull(username, "Username is required");
        Objects.requireNonNull(item, "Item is required");
        RoomId roomId = ensurePlayerLocation(username);
        addItem(roomId, item);
        return true;
    }

    /**
     * Produces a look description for the player's current room.
     */
    public LookResult look(Username username) {
        Objects.requireNonNull(username, "Username is required");
        Room room = loadRoomForPlayer(username);
        if (room == null) {
            return new LookResult(List.of("You are nowhere. The world feels unfinished."), null);
        }
        Room roomWithOccupants = withOccupants(room);
        return new LookResult(describeRoom(roomWithOccupants, username), roomWithOccupants);
    }

    /**
     * Attempts to move the player in the provided direction.
     */
    public MoveResult move(Username username, Direction direction) {
        Objects.requireNonNull(username, "Username is required");
        Objects.requireNonNull(direction, "Direction is required");
        Room room = loadRoomForPlayer(username);
        if (room == null) {
            return new MoveResult(false, List.of("You cannot move yet. The world has no rooms."), null);
        }
        RoomId destinationId = room.getExits().get(direction);
        if (destinationId == null) {
            return new MoveResult(false, List.of("You cannot go " + direction.label() + "."), room);
        }
        if (isExitLocked(room.getId(), direction)) {
            return new MoveResult(false, List.of("The door to the " + direction.label() + " is locked."), room);
        }
        Room destination = findRoom(destinationId).orElse(null);
        if (destination == null) {
            return new MoveResult(false, List.of("The way " + direction.label() + " is blocked."), room);
        }
        playerLocations.put(username, destinationId);
        Room destinationWithOccupants = withOccupants(destination);
        List<String> lines = new ArrayList<>();
        lines.add("You move " + direction.label() + ".");
        lines.addAll(describeRoom(destinationWithOccupants, username));
        return new MoveResult(true, lines, destinationWithOccupants);
    }

    private Room loadRoomForPlayer(Username username) {
        RoomId roomId = ensurePlayerLocation(username);
        return findRoom(roomId).orElse(null);
    }

    private Optional<Room> findRoom(RoomId roomId) {
        try {
            return roomRepository.findById(roomId);
        } catch (RepositoryException e) {
            return Optional.empty();
        }
    }

    private Room withOccupants(Room room) {
        List<Username> occupants = playerLocations.entrySet().stream()
            .filter(entry -> entry.getValue().equals(room.getId()))
            .map(Entry::getKey)
            .toList();
        List<Item> items = mergeItems(room);
        return new Room(
            room.getId(),
            room.getName(),
            room.getDescription(),
            room.getExits(),
            items,
            occupants,
            room.getLockedExits()
        );
    }

    private List<String> describeRoom(Room room, Username viewer) {
        List<String> lines = new ArrayList<>();
        lines.add(room.getName());
        lines.add(room.getDescription());
        lines.add("Exits: " + formatExits(room.getId(), room.getExits()));
        lines.add("Items: " + formatItems(room.getItems()));
        lines.add("Occupants: " + formatOccupants(room.getOccupants(), viewer));
        return lines;
    }

    private String formatExits(RoomId roomId, Map<Direction, RoomId> exits) {
        if (exits.isEmpty()) {
            return "none";
        }
        Set<Direction> locked = ensureLockedState(roomId);
        return exits.keySet().stream()
            .sorted(Comparator.comparing(Direction::label))
            .map(dir -> locked.contains(dir) ? dir.label() + " [locked]" : dir.label())
            .collect(Collectors.joining(", "));
    }

    private String formatItems(List<Item> items) {
        if (items.isEmpty()) {
            return "none";
        }
        return items.stream()
            .map(Item::getName)
            .collect(Collectors.joining(", "));
    }

    private String formatOccupants(List<Username> occupants, Username viewer) {
        List<String> names = occupants.stream()
            .filter(username -> !username.equals(viewer))
            .map(Username::getValue)
            .toList();
        if (names.isEmpty()) {
            return "none";
        }
        return String.join(", ", names);
    }

    private List<Item> mergeItems(Room room) {
        List<Item> extras = transientItems.get(room.getId());
        if (extras == null || extras.isEmpty()) {
            return room.getItems();
        }
        List<Item> merged = new ArrayList<>(room.getItems());
        merged.addAll(extras);
        return List.copyOf(merged);
    }

    private Item matchItem(List<Item> items, String input) {
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        for (Item item : items) {
            String name = item.getName().toLowerCase(Locale.ROOT);
            if (name.equals(normalized) || name.startsWith(normalized)) {
                return item;
            }
            String id = item.getId().getValue().toLowerCase(Locale.ROOT);
            if (id.equals(normalized) || id.startsWith(normalized)) {
                return item;
            }
        }
        return null;
    }

    private void removeRoomItem(Room room, Item match) {
        List<Item> nextItems = new ArrayList<>(room.getItems());
        nextItems.removeIf(item -> item.getId().equals(match.getId()));
        Room updated = new Room(
            room.getId(),
            room.getName(),
            room.getDescription(),
            room.getExits(),
            nextItems,
            room.getOccupants(),
            room.getLockedExits()
        );
        try {
            roomRepository.save(updated);
        } catch (RepositoryException e) {
            // fallback: no-op if room persistence fails
        }
    }

    private void removeTransientItem(RoomId roomId, Item match) {
        transientItems.computeIfPresent(roomId, (id, existing) -> {
            List<Item> next = new ArrayList<>(existing);
            next.removeIf(item -> item.getId().equals(match.getId()));
            if (next.isEmpty()) {
                return null;
            }
            return List.copyOf(next);
        });
    }

    private void removeTransientItemById(RoomId roomId, ItemId itemId) {
        transientItems.computeIfPresent(roomId, (id, existing) -> {
            List<Item> next = new ArrayList<>(existing);
            next.removeIf(item -> item.getId().equals(itemId));
            return next.isEmpty() ? null : List.copyOf(next);
        });
    }

    /**
     * Attempts to unlock the exit in the given direction from the player's current room.
     *
     * <p>Succeeds when the exit is declared lockable in the room data, is currently locked,
     * and the player's inventory contains the required key item.
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
        Room room = loadRoomForPlayer(username);
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
     * inventory contains the required key item.
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
        Room room = loadRoomForPlayer(username);
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

    private boolean isExitLocked(RoomId roomId, Direction direction) {
        return ensureLockedState(roomId).contains(direction);
    }

    /**
     * Returns (and lazily seeds) the runtime locked-exit set for a room.
     *
     * <p>The first call for a given room id loads the room from the repository and
     * initialises the set from the room's {@link Room#getLockedExits()} configuration.
     */
    private Set<Direction> ensureLockedState(RoomId roomId) {
        return runtimeLockedExits.computeIfAbsent(roomId, id -> {
            Set<Direction> set = ConcurrentHashMap.newKeySet();
            findRoom(id).ifPresent(r -> set.addAll(r.getLockedExits().keySet()));
            return set;
        });
    }

    /**
     * Propagates a lock state change to the opposite side of the door (if the destination
     * room also declares the reverse direction as a lockable exit).
     */
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

    private Item createCorpse(Username username, int gold) {
        String owner = username.getValue();
        String id = "corpse-" + owner.toLowerCase(Locale.ROOT) + "-" + corpseCounter.incrementAndGet();
        String description = gold > 0
            ? "The corpse of " + owner + " lies here, containing "
                + gold + " gold coin" + (gold == 1 ? "" : "s") + "."
            : "The corpse of " + owner + " lies here.";
        return new Item(
            ItemId.of(id),
            "the corpse of " + owner,
            description,
            ItemAttributes.empty(),
            List.of(),
            List.of(),
            null,
            0,
            gold,
            null
        );
    }
}

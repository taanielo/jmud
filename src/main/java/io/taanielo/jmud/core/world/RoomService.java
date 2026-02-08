package io.taanielo.jmud.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
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

    private final RoomRepository roomRepository;
    private final RoomId startingRoomId;
    private final ConcurrentHashMap<Username, RoomId> playerLocations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<RoomId, List<Item>> transientItems = new ConcurrentHashMap<>();
    private final AtomicLong corpseCounter = new AtomicLong();

    /**
     * Creates a room service with the provided repository and starting room id.
     */
    public RoomService(RoomRepository roomRepository, RoomId startingRoomId) {
        this.roomRepository = Objects.requireNonNull(roomRepository, "Room repository is required");
        this.startingRoomId = Objects.requireNonNull(startingRoomId, "Starting room id is required");
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
     * Spawns a corpse item in the specified room.
     */
    public Item spawnCorpse(Username username, RoomId roomId) {
        Objects.requireNonNull(username, "Username is required");
        Objects.requireNonNull(roomId, "Room id is required");
        Item corpse = createCorpse(username);
        addItem(roomId, corpse);
        return corpse;
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
            occupants
        );
    }

    private List<String> describeRoom(Room room, Username viewer) {
        List<String> lines = new ArrayList<>();
        lines.add(room.getName());
        lines.add(room.getDescription());
        lines.add("Exits: " + formatExits(room.getExits()));
        lines.add("Items: " + formatItems(room.getItems()));
        lines.add("Occupants: " + formatOccupants(room.getOccupants(), viewer));
        return lines;
    }

    private String formatExits(java.util.Map<Direction, RoomId> exits) {
        if (exits.isEmpty()) {
            return "none";
        }
        return exits.keySet().stream()
            .map(Direction::label)
            .sorted()
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
            room.getOccupants()
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

    private Item createCorpse(Username username) {
        String owner = username.getValue();
        String id = "corpse-" + owner.toLowerCase(Locale.ROOT) + "-" + corpseCounter.incrementAndGet();
        return new Item(
            ItemId.of(id),
            "Corpse of " + owner,
            "The corpse of " + owner + " lies here.",
            ItemAttributes.empty(),
            List.of(),
            List.of(),
            null,
            0,
            0
        );
    }
}

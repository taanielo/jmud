package io.taanielo.jmud.core.world;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.RoomRepository;

/**
 * Façade that coordinates {@link PlayerLocationService}, {@link RoomItemService}, and
 * {@link RoomRenderer} for composite operations such as look, move, take, and drop.
 *
 * <p>Pure location and item operations are delegated directly to the focused services. This class
 * exists to preserve existing caller call-sites while the three services are introduced; callers
 * should migrate to the focused services over time, after which this façade can be removed.
 *
 * <p>The convenience constructor {@link #RoomService(RoomRepository, RoomId)} creates the three
 * sub-services internally and is provided for legacy call sites (primarily tests).
 */
public class RoomService {

    /**
     * Result of a look action, including rendered lines and the resolved room.
     */
    public record LookResult(List<String> lines, @Nullable Room room) {
    }

    /**
     * Result of a move action.
     */
    public record MoveResult(boolean moved, List<String> lines, @Nullable Room room) {
    }

    private final PlayerLocationService locationService;
    private final RoomItemService itemService;
    private final RoomRenderer renderer;
    private final RoomRepository roomRepository;
    /**
     * Optional world clock used to select day/night room descriptions; {@code null} means the
     * cycle is disabled and rooms always render their day description.
     */
    private @Nullable WorldClock worldClock;

    /**
     * Creates a room service façade wrapping the three focused services.
     *
     * @param locationService the player location and movement service
     * @param itemService     the transient item and corpse service
     * @param renderer        the stateless room renderer
     * @param roomRepository  the room data store (used for static-item take operations)
     */
    public RoomService(
            PlayerLocationService locationService,
            RoomItemService itemService,
            RoomRenderer renderer,
            RoomRepository roomRepository) {
        this.locationService = Objects.requireNonNull(locationService, "Location service is required");
        this.itemService = Objects.requireNonNull(itemService, "Item service is required");
        this.renderer = Objects.requireNonNull(renderer, "Room renderer is required");
        this.roomRepository = Objects.requireNonNull(roomRepository, "Room repository is required");
    }

    /**
     * Convenience constructor that creates the three focused services internally.
     *
     * <p>Provided for backward compatibility with test call sites. Production code should prefer
     * the full constructor so that each service can be injected independently (e.g. to pass
     * {@link RoomItemService} to {@link CorpseDecayTicker}).
     *
     * @param roomRepository the room data store
     * @param startingRoomId the room id used when a player has no location
     */
    public RoomService(RoomRepository roomRepository, RoomId startingRoomId) {
        this(
            new PlayerLocationService(roomRepository, startingRoomId),
            new RoomItemService(),
            new RoomRenderer(),
            roomRepository
        );
    }

    /**
     * Registers the world clock consulted to pick day/night room descriptions.
     *
     * @param worldClock the world clock; may be null to disable the day/night cycle
     */
    public void setWorldClock(@Nullable WorldClock worldClock) {
        this.worldClock = worldClock;
    }

    // ── Location delegation ──────────────────────────────────────────────────

    /**
     * Returns all player usernames currently in the given room.
     */
    public List<Username> getPlayersInRoom(RoomId roomId) {
        return locationService.getPlayersInRoom(roomId);
    }

    /**
     * Ensures a player has a location, defaulting to the starting room if missing.
     */
    public RoomId ensurePlayerLocation(Username username) {
        return locationService.ensurePlayerLocation(username);
    }

    /**
     * Returns the current player location, if any.
     */
    public Optional<RoomId> findPlayerLocation(Username username) {
        return locationService.findPlayerLocation(username);
    }

    /**
     * Removes a player from the room tracking map.
     */
    public void clearPlayerLocation(Username username) {
        locationService.clearPlayerLocation(username);
    }

    /**
     * Respawns a player at the starting room.
     */
    public RoomId respawnPlayer(Username username) {
        return locationService.respawnPlayer(username);
    }

    /**
     * Returns the exit map for the given room, or an empty map if the room cannot be found.
     */
    public Map<Direction, RoomId> getExits(RoomId roomId) {
        return locationService.getExits(roomId);
    }

    // ── Item delegation ──────────────────────────────────────────────────────

    /**
     * Adds a transient item to the specified room.
     */
    public void addItem(RoomId roomId, Item item) {
        itemService.addItem(roomId, item);
    }

    /**
     * Spawns a corpse item in the specified room.
     */
    public Corpse spawnCorpse(Username username, RoomId roomId, int gold) {
        return itemService.spawnCorpse(username, roomId, gold);
    }

    /**
     * Removes all tracked corpses older than the given duration.
     *
     * @deprecated Prefer {@link RoomItemService#removeExpiredCorpses(Duration)} directly.
     *             {@link CorpseDecayTicker} now accepts {@link RoomItemService} and no longer
     *             uses this delegation path.
     */
    @Deprecated
    public void removeExpiredCorpses(Duration decayAfter) {
        itemService.removeExpiredCorpses(decayAfter);
    }

    // ── Lock / Unlock delegation ─────────────────────────────────────────────

    /**
     * Attempts to unlock the exit in the given direction from the player's current room.
     */
    public DoorActionResult unlock(Username username, Direction direction, List<Item> inventory) {
        return locationService.unlock(username, direction, inventory);
    }

    /**
     * Attempts to lock the exit in the given direction from the player's current room.
     */
    public DoorActionResult lock(Username username, Direction direction, List<Item> inventory) {
        return locationService.lock(username, direction, inventory);
    }

    // ── Orchestrated operations ──────────────────────────────────────────────

    /**
     * Produces a look description for the player's current room.
     */
    public LookResult look(Username username) {
        Objects.requireNonNull(username, "Username is required");
        Optional<Room> roomOpt = locationService.loadRoomForPlayer(username);
        if (roomOpt.isEmpty()) {
            return new LookResult(List.of("You are nowhere. The world feels unfinished."), null);
        }
        Room room = roomOpt.get();
        Room enriched = enrichRoom(room);
        Set<Direction> lockedExits = locationService.getLockedExits(room.getId());
        return new LookResult(renderer.describeRoom(enriched, username, lockedExits, currentTimeOfDay()), enriched);
    }

    /**
     * Attempts to move the player in the provided direction.
     */
    public MoveResult move(Username username, Direction direction) {
        Objects.requireNonNull(username, "Username is required");
        Objects.requireNonNull(direction, "Direction is required");
        PlayerLocationService.MoveAttempt attempt = locationService.attemptMove(username, direction);
        return switch (attempt) {
            case PlayerLocationService.MoveAttempt.Failed f ->
                new MoveResult(false, List.of(f.reason()), f.currentRoom());
            case PlayerLocationService.MoveAttempt.Succeeded s -> {
                Room enriched = enrichRoom(s.destination());
                Set<Direction> lockedExits = locationService.getLockedExits(enriched.getId());
                List<String> lines = new ArrayList<>();
                lines.add("You move " + direction.label() + ".");
                lines.addAll(renderer.describeRoom(enriched, username, lockedExits, currentTimeOfDay()));
                yield new MoveResult(true, lines, enriched);
            }
        };
    }

    /**
     * Removes an item from the player's current room (checks static room items first, then
     * transient items).
     */
    public Optional<Item> takeItem(Username username, String input) {
        Objects.requireNonNull(username, "Username is required");
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        Optional<Room> roomOpt = locationService.loadRoomForPlayer(username);
        if (roomOpt.isEmpty()) {
            return Optional.empty();
        }
        Room room = roomOpt.get();
        Item match = matchItem(room.getItems(), input);
        if (match != null) {
            removeRoomItem(room, match);
            return Optional.of(match);
        }
        return itemService.takeTransientItem(room.getId(), input);
    }

    /**
     * Drops an item into the player's current room.
     */
    public boolean dropItem(Username username, Item item) {
        Objects.requireNonNull(username, "Username is required");
        Objects.requireNonNull(item, "Item is required");
        RoomId roomId = locationService.ensurePlayerLocation(username);
        itemService.addItem(roomId, item);
        return true;
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private TimeOfDay currentTimeOfDay() {
        return worldClock != null ? worldClock.timeOfDay() : TimeOfDay.DAY;
    }

    private Room enrichRoom(Room room) {
        List<Username> occupants = locationService.getPlayersInRoom(room.getId());
        List<Item> items = itemService.mergeItems(room);
        return new Room(
            room.getId(),
            room.getName(),
            room.getDescription(),
            room.getExits(),
            items,
            occupants,
            room.getLockedExits(),
            room.getMinLevel(),
            room.getNightDescription()
        );
    }

    private @Nullable Item matchItem(List<Item> items, String input) {
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
            room.getLockedExits(),
            room.getMinLevel(),
            room.getNightDescription()
        );
        try {
            roomRepository.save(updated);
        } catch (RepositoryException e) {
            // fallback: no-op if room persistence fails
        }
    }
}

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
import io.taanielo.jmud.core.output.PlainTextStyler;
import io.taanielo.jmud.core.output.TextStyler;
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

    private static final TextStyler PLAIN = new PlainTextStyler();

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
     * Optional weather view used to append weather lines to outdoor room descriptions; {@code null}
     * means the weather subsystem is disabled and rooms render without weather.
     */
    private @Nullable RoomWeatherView weatherView;

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

    /**
     * Registers the weather view consulted to append weather lines to outdoor room descriptions.
     *
     * @param weatherView the weather view; may be null to disable weather rendering
     */
    public void setWeatherView(@Nullable RoomWeatherView weatherView) {
        this.weatherView = weatherView;
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
     * Relocates a player directly to the given room, bypassing exit and lock checks.
     *
     * <p>Used when a player is moved by fiat rather than by walking — for example the Cleric
     * resurrection spell placing a revived party member into the caster's room. Tick-thread only
     * (AGENTS.md §5).
     *
     * @param username the player to relocate
     * @param roomId   the destination room id
     */
    public void movePlayerTo(Username username, RoomId roomId) {
        locationService.movePlayerTo(username, roomId);
    }

    /**
     * Returns the exit map for the given room, or an empty map if the room cannot be found.
     */
    public Map<Direction, RoomId> getExits(RoomId roomId) {
        return locationService.getExits(roomId);
    }

    /**
     * Looks up a room by its id directly from the room store, bypassing player-location resolution.
     *
     * <p>Used by administrative commands (e.g. wizard {@code GOTO}/{@code SPAWN}) that must validate
     * an operator-supplied room id before teleporting a player or spawning a mob into it.
     *
     * @param roomId the room id to resolve
     * @return the room, or empty when no room with that id exists
     */
    public Optional<Room> findRoom(RoomId roomId) {
        Objects.requireNonNull(roomId, "Room id is required");
        try {
            return roomRepository.findById(roomId);
        } catch (RepositoryException e) {
            return Optional.empty();
        }
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
     * Finds the tracked corpse belonging to the named owner, if one is still present.
     *
     * @param ownerName the name of the dead player whose corpse to find
     * @return the tracked corpse, or empty when none is currently tracked for that owner
     */
    public Optional<Corpse> findCorpseByOwner(String ownerName) {
        return itemService.findCorpseByOwner(ownerName);
    }

    /**
     * Removes a specific tracked corpse and its ground item from the world (consumed, not decayed).
     *
     * @param corpse the corpse to remove
     * @return {@code true} if the corpse was tracked and has now been removed
     */
    public boolean removeCorpse(Corpse corpse) {
        return itemService.removeCorpse(corpse);
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
        return look(username, PLAIN);
    }

    /**
     * Produces a look description for the player's current room, coloring room item names by their
     * rarity tier through the supplied {@link TextStyler}.
     *
     * @param username the player looking
     * @param styler   the styler used to color item names by rarity
     * @return the rendered look result
     */
    public LookResult look(Username username, TextStyler styler) {
        Objects.requireNonNull(username, "Username is required");
        Objects.requireNonNull(styler, "Styler is required");
        Optional<Room> roomOpt = locationService.loadRoomForPlayer(username);
        if (roomOpt.isEmpty()) {
            return new LookResult(List.of("You are nowhere. The world feels unfinished."), null);
        }
        Room room = roomOpt.get();
        Room enriched = enrichRoom(room);
        Set<Direction> lockedExits = locationService.getLockedExits(room.getId());
        List<String> lines =
            new ArrayList<>(renderer.describeRoom(enriched, username, lockedExits, currentTimeOfDay(), styler));
        appendWeatherLines(lines, enriched);
        return new LookResult(lines, enriched);
    }

    /**
     * Attempts to move the player in the provided direction.
     */
    public MoveResult move(Username username, Direction direction) {
        return move(username, direction, PLAIN);
    }

    /**
     * Attempts to move the player in the provided direction, coloring room item names in the
     * destination's look description by their rarity tier through the supplied {@link TextStyler}.
     *
     * @param username  the player moving
     * @param direction the direction to move
     * @param styler    the styler used to color item names by rarity
     * @return the rendered move result
     */
    public MoveResult move(Username username, Direction direction, TextStyler styler) {
        Objects.requireNonNull(username, "Username is required");
        Objects.requireNonNull(direction, "Direction is required");
        Objects.requireNonNull(styler, "Styler is required");
        PlayerLocationService.MoveAttempt attempt = locationService.attemptMove(username, direction);
        return switch (attempt) {
            case PlayerLocationService.MoveAttempt.Failed f ->
                new MoveResult(false, List.of(f.reason()), f.currentRoom());
            case PlayerLocationService.MoveAttempt.Succeeded s -> {
                Room enriched = enrichRoom(s.destination());
                Set<Direction> lockedExits = locationService.getLockedExits(enriched.getId());
                List<String> lines = new ArrayList<>();
                lines.add("You move " + direction.label() + ".");
                lines.addAll(renderer.describeRoom(enriched, username, lockedExits, currentTimeOfDay(), styler));
                appendWeatherLines(lines, enriched);
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
     * Finds an item in the player's current room by name or id prefix, searching both static
     * (room-defined) and transient items.
     *
     * @param username the player whose room is searched
     * @param input    the item name or id prefix to match (case-insensitive)
     * @return the matched item, or empty if none matches
     */
    public Optional<Item> findItem(Username username, String input) {
        Objects.requireNonNull(username, "Username is required");
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        Optional<Room> roomOpt = locationService.loadRoomForPlayer(username);
        if (roomOpt.isEmpty()) {
            return Optional.empty();
        }
        List<Item> merged = itemService.mergeItems(roomOpt.get());
        return Optional.ofNullable(matchItem(merged, input));
    }

    /**
     * Replaces an item in the player's current room with a new copy carrying the same id, used to
     * apply in-place state changes such as unlocking a container. Static (room-defined) items are
     * persisted through the room repository; transient items are swapped in place.
     *
     * @param username    the player whose room holds the item
     * @param itemId      the id of the item to replace
     * @param replacement the replacement item (must share {@code itemId})
     * @return {@code true} if an item was replaced, {@code false} if none matched
     */
    public boolean replaceItem(Username username, ItemId itemId, Item replacement) {
        Objects.requireNonNull(username, "Username is required");
        Objects.requireNonNull(itemId, "Item id is required");
        Objects.requireNonNull(replacement, "Replacement item is required");
        Optional<Room> roomOpt = locationService.loadRoomForPlayer(username);
        if (roomOpt.isEmpty()) {
            return false;
        }
        Room room = roomOpt.get();
        boolean isStatic = room.getItems().stream().anyMatch(item -> item.getId().equals(itemId));
        if (isStatic) {
            List<Item> nextItems = new ArrayList<>();
            for (Item item : room.getItems()) {
                nextItems.add(item.getId().equals(itemId) ? replacement : item);
            }
            Room updated = new Room(
                room.getId(),
                room.getName(),
                room.getDescription(),
                room.getExits(),
                nextItems,
                room.getOccupants(),
                room.getLockedExits(),
                room.getMinLevel(),
                room.getNightDescription(),
                room.getLightLevel(),
                room.isOutdoor()
            );
            try {
                roomRepository.save(updated);
            } catch (RepositoryException e) {
                // fallback: no-op if room persistence fails
            }
            return true;
        }
        boolean isTransient = itemService.getTransientItems(room.getId()).stream()
            .anyMatch(item -> item.getId().equals(itemId));
        if (isTransient) {
            itemService.removeTransientItemById(room.getId(), itemId);
            itemService.addItem(room.getId(), replacement);
            return true;
        }
        return false;
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

    private void appendWeatherLines(List<String> lines, Room room) {
        if (weatherView != null) {
            lines.addAll(weatherView.weatherLines(room));
        }
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
            room.getNightDescription(),
            room.getLightLevel(),
            room.isOutdoor()
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
            room.getNightDescription(),
            room.getLightLevel(),
            room.isOutdoor()
        );
        try {
            roomRepository.save(updated);
        } catch (RepositoryException e) {
            // fallback: no-op if room persistence fails
        }
    }
}

package io.taanielo.jmud.core.world;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.authentication.Username;

/**
 * Domain service that owns the transient ground-item and corpse state for all rooms.
 *
 * <p>Static items (those defined in room data files) are owned by the repository; this service
 * owns only runtime-transient items: dropped loot and player corpses spawned on death.
 *
 * <p>All mutation happens on the tick thread (AGENTS.md §5).
 */
public class RoomItemService {

    private final ConcurrentHashMap<RoomId, List<Item>> transientItems = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Corpse> trackedCorpses = new ConcurrentLinkedQueue<>();
    private final AtomicLong corpseCounter = new AtomicLong();

    /**
     * Adds a transient item to the specified room.
     *
     * @param roomId the room to add the item to
     * @param item   the item to add
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
     * Returns the transient items currently in the given room, or an empty list if none.
     *
     * @param roomId the room to query
     * @return an unmodifiable list of transient items
     */
    public List<Item> getTransientItems(RoomId roomId) {
        Objects.requireNonNull(roomId, "Room id is required");
        List<Item> items = transientItems.get(roomId);
        return items == null ? List.of() : items;
    }

    /**
     * Returns the combined list of static (room-defined) and transient items for the given room.
     *
     * @param room the room carrying its static items
     * @return an unmodifiable merged item list
     */
    public List<Item> mergeItems(Room room) {
        Objects.requireNonNull(room, "Room is required");
        List<Item> extras = transientItems.get(room.getId());
        if (extras == null || extras.isEmpty()) {
            return room.getItems();
        }
        List<Item> merged = new ArrayList<>(room.getItems());
        merged.addAll(extras);
        return List.copyOf(merged);
    }

    /**
     * Spawns a corpse item in the specified room, carrying the given amount of gold.
     *
     * <p>The spawned corpse is tracked internally and will be removed after the configured
     * decay period by {@link #removeExpiredCorpses(Duration)}.
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
     * Attempts to find and remove a transient item from the specified room by name or id prefix.
     *
     * @param roomId the room to search
     * @param input  the name or id prefix to match (case-insensitive)
     * @return the removed item, or empty if not found
     */
    public Optional<Item> takeTransientItem(RoomId roomId, String input) {
        Objects.requireNonNull(roomId, "Room id is required");
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        List<Item> extras = transientItems.get(roomId);
        if (extras == null || extras.isEmpty()) {
            return Optional.empty();
        }
        Item match = matchItem(extras, input);
        if (match == null) {
            return Optional.empty();
        }
        removeTransientItem(roomId, match);
        return Optional.of(match);
    }

    /**
     * Removes a specific transient item from a room by its id.
     *
     * @param roomId the room containing the item
     * @param itemId the id of the item to remove
     */
    public void removeTransientItemById(RoomId roomId, ItemId itemId) {
        transientItems.computeIfPresent(roomId, (id, existing) -> {
            List<Item> next = new ArrayList<>(existing);
            next.removeIf(item -> item.getId().equals(itemId));
            return next.isEmpty() ? null : List.copyOf(next);
        });
    }

    private void removeTransientItem(RoomId roomId, Item match) {
        transientItems.computeIfPresent(roomId, (id, existing) -> {
            List<Item> next = new ArrayList<>(existing);
            next.removeIf(item -> item.getId().equals(match.getId()));
            return next.isEmpty() ? null : List.copyOf(next);
        });
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

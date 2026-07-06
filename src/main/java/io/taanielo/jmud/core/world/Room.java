package io.taanielo.jmud.core.world;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import lombok.Value;

import io.taanielo.jmud.core.authentication.Username;

@Value
public class Room {
    RoomId id;
    String name;
    String description;
    Map<Direction, RoomId> exits;
    List<Item> items;
    List<Username> occupants;
    /** Exits that start locked and the key item id required to unlock each one. */
    Map<Direction, ItemId> lockedExits;
    /** Recommended/minimum level to enter this room, advisory only; {@code null} means no restriction. */
    @Nullable Integer minLevel;

    /**
     * Constructs a room with no locked exits. Existing callsites use this overload.
     */
    public Room(
        RoomId id,
        String name,
        String description,
        Map<Direction, RoomId> exits,
        List<Item> items,
        List<Username> occupants
    ) {
        this(id, name, description, exits, items, occupants, Map.of(), null);
    }

    /**
     * Constructs a room with the specified locked exits configuration and no level restriction.
     *
     * @param lockedExits map of direction to required key item id for exits that start locked
     */
    public Room(
        RoomId id,
        String name,
        String description,
        Map<Direction, RoomId> exits,
        List<Item> items,
        List<Username> occupants,
        Map<Direction, ItemId> lockedExits
    ) {
        this(id, name, description, exits, items, occupants, lockedExits, null);
    }

    /**
     * Constructs a room with the specified locked exits configuration and advisory minimum level.
     *
     * @param lockedExits map of direction to required key item id for exits that start locked
     * @param minLevel    the recommended/minimum level to enter this room, or {@code null} if none
     */
    public Room(
        RoomId id,
        String name,
        String description,
        Map<Direction, RoomId> exits,
        List<Item> items,
        List<Username> occupants,
        Map<Direction, ItemId> lockedExits,
        @Nullable Integer minLevel
    ) {
        this.id = Objects.requireNonNull(id, "Room id is required");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Room name must not be blank");
        }
        this.name = name;
        this.description = Objects.requireNonNull(description, "Room description is required");
        this.exits = Map.copyOf(Objects.requireNonNull(exits, "Room exits are required"));
        this.items = List.copyOf(Objects.requireNonNull(items, "Room items are required"));
        this.occupants = List.copyOf(Objects.requireNonNull(occupants, "Room occupants are required"));
        this.lockedExits = Map.copyOf(Objects.requireNonNull(lockedExits, "Locked exits map is required"));
        this.minLevel = minLevel;
    }

    /**
     * Returns whether this room's recommended/minimum level exceeds the given player level. This
     * check is advisory only: callers use it to decide whether to show a warning, never to block
     * or delay movement.
     *
     * @param playerLevel the level of the player entering the room
     * @return {@code true} if this room has a minimum level set and it is higher than {@code playerLevel}
     */
    public boolean exceedsLevel(int playerLevel) {
        return minLevel != null && minLevel > playerLevel;
    }
}

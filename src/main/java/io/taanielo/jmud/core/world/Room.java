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
     * Optional alternate description shown instead of {@link #description} when the world is in
     * {@link TimeOfDay#NIGHT}; {@code null} means the room looks the same at night.
     */
    @Nullable String nightDescription;

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
     * Constructs a room with the specified locked exits configuration and advisory minimum level,
     * with no alternate night description.
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
        this(id, name, description, exits, items, occupants, lockedExits, minLevel, null);
    }

    /**
     * Constructs a room with the specified locked exits configuration, advisory minimum level, and
     * alternate night description.
     *
     * @param lockedExits       map of direction to required key item id for exits that start locked
     * @param minLevel          the recommended/minimum level to enter this room, or {@code null} if none
     * @param nightDescription  the description shown at night instead of {@code description}, or
     *                          {@code null} if the room looks the same at night
     */
    public Room(
        RoomId id,
        String name,
        String description,
        Map<Direction, RoomId> exits,
        List<Item> items,
        List<Username> occupants,
        Map<Direction, ItemId> lockedExits,
        @Nullable Integer minLevel,
        @Nullable String nightDescription
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
        this.nightDescription = nightDescription;
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

    /**
     * Returns the description to show for the given time of day: {@link #nightDescription} when
     * {@code timeOfDay} is {@link TimeOfDay#NIGHT} and one is defined, otherwise {@link #description}.
     *
     * @param timeOfDay the current time of day
     * @return the description text to render
     */
    public String describeFor(TimeOfDay timeOfDay) {
        Objects.requireNonNull(timeOfDay, "Time of day is required");
        if (timeOfDay == TimeOfDay.NIGHT && nightDescription != null) {
            return nightDescription;
        }
        return description;
    }
}

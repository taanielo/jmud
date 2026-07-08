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
     * Optional ambient light level: {@code 0} = pitch dark, {@code 1} = dim, {@code 2} or higher =
     * naturally lit; {@code null} means the room is naturally lit and needs no light source. Dark
     * rooms hide their contents from players who carry no sufficient light source; see
     * {@link #requiredLightRadius()}.
     */
    @Nullable Integer lightLevel;

    /**
     * Light level at or above which a room is considered naturally lit (needs no carried light).
     */
    private static final int LIT_LEVEL = 2;

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
        this(id, name, description, exits, items, occupants, lockedExits, minLevel, nightDescription, null);
    }

    /**
     * Constructs a room with the specified locked exits configuration, advisory minimum level,
     * alternate night description, and ambient light level.
     *
     * @param lockedExits       map of direction to required key item id for exits that start locked
     * @param minLevel          the recommended/minimum level to enter this room, or {@code null} if none
     * @param nightDescription  the description shown at night instead of {@code description}, or
     *                          {@code null} if the room looks the same at night
     * @param lightLevel        the ambient light level ({@code 0} pitch dark, {@code 1} dim,
     *                          {@code 2}+ lit), or {@code null} for a naturally lit room
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
        @Nullable String nightDescription,
        @Nullable Integer lightLevel
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
        this.lightLevel = lightLevel;
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

    /**
     * Returns the minimum carried light radius a player needs to see this room's full contents.
     *
     * <p>A naturally lit room ({@link #lightLevel} {@code null} or {@code >= 2}) returns {@code 0},
     * meaning no light is required. Darker rooms return a positive threshold that scales with how
     * dark the room is: a pitch-dark room ({@code light_level = 0}) needs radius {@code 1} (a torch
     * suffices), while a dim room ({@code light_level = 1}) needs radius {@code 2} (only a brighter
     * source such as a lantern reveals it).
     *
     * @return the required light radius, or {@code 0} when no light source is needed
     */
    public int requiredLightRadius() {
        if (lightLevel == null || lightLevel >= LIT_LEVEL) {
            return 0;
        }
        return lightLevel + 1;
    }

    /**
     * Returns whether this room is dark enough to require a carried light source to see its
     * contents.
     *
     * @return {@code true} if {@link #requiredLightRadius()} is positive
     */
    public boolean requiresLight() {
        return requiredLightRadius() > 0;
    }
}

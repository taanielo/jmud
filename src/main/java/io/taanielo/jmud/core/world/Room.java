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
    /** Recommended/minimum level to enter this room, advisory only; null means no restriction. */
    @Nullable Integer minLevel;
    /**
     * Optional alternate description shown instead of the description field when the world is in
     * TimeOfDay.NIGHT; null means the room looks the same at night.
     */
    @Nullable String nightDescription;
    /**
     * Optional ambient light level: 0 = pitch dark, 1 = dim, 2 or higher = naturally lit; null means
     * the room is naturally lit and needs no light source. Dark rooms hide their contents from
     * players who carry no sufficient light source; see requiredLightRadius().
     */
    @Nullable Integer lightLevel;
    /**
     * Whether this room is exposed to the sky, and therefore subject to the world's dynamic
     * weather; false means the room is indoors and never shows weather effects.
     */
    boolean outdoor;
    /**
     * Optional pool of atmospheric flavour lines the room emits occasionally during play (e.g.
     * dripping water, distant howls). Empty means the room is silent; never null.
     */
    List<String> ambientMessages;
    /**
     * Optional secret exits keyed by direction to their destination room. Hidden exits are omitted
     * from the room's rendered exit line and cannot be walked through with a normal direction
     * command until a player has discovered them via the SEARCH command. Discovery state is tracked
     * at runtime by the player location service, never on this immutable value object. Empty means
     * the room has no secret exits; never null.
     */
    Map<Direction, RoomId> hiddenExits;
    /**
     * Optional standing environmental hazard: when set, every player physically present in this
     * room periodically takes typed, resistance-mitigated damage. The hazard is always surfaced in
     * the rendered room description so it is never a surprise. A null value means the terrain is
     * inert and deals no damage.
     */
    @Nullable RoomHazard hazard;

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
        this(id, name, description, exits, items, occupants, lockedExits, minLevel, nightDescription,
            lightLevel, false);
    }

    /**
     * Constructs a room with all optional attributes including the outdoor/weather flag but no
     * ambient messages.
     *
     * @param lockedExits       map of direction to required key item id for exits that start locked
     * @param minLevel          the recommended/minimum level to enter this room, or {@code null} if none
     * @param nightDescription  the description shown at night instead of {@code description}, or
     *                          {@code null} if the room looks the same at night
     * @param lightLevel        the ambient light level ({@code 0} pitch dark, {@code 1} dim,
     *                          {@code 2}+ lit), or {@code null} for a naturally lit room
     * @param outdoor           {@code true} if the room is exposed to the sky and subject to weather
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
        @Nullable Integer lightLevel,
        boolean outdoor
    ) {
        this(id, name, description, exits, items, occupants, lockedExits, minLevel, nightDescription,
            lightLevel, outdoor, List.of());
    }

    /**
     * Constructs a room with all optional attributes including the ambient message pool.
     *
     * @param lockedExits       map of direction to required key item id for exits that start locked
     * @param minLevel          the recommended/minimum level to enter this room, or {@code null} if none
     * @param nightDescription  the description shown at night instead of {@code description}, or
     *                          {@code null} if the room looks the same at night
     * @param lightLevel        the ambient light level ({@code 0} pitch dark, {@code 1} dim,
     *                          {@code 2}+ lit), or {@code null} for a naturally lit room
     * @param outdoor           {@code true} if the room is exposed to the sky and subject to weather
     * @param ambientMessages   the pool of atmospheric flavour lines the room emits occasionally
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
        @Nullable Integer lightLevel,
        boolean outdoor,
        List<String> ambientMessages
    ) {
        this(id, name, description, exits, items, occupants, lockedExits, minLevel, nightDescription,
            lightLevel, outdoor, ambientMessages, Map.of());
    }

    /**
     * Constructs a room with all optional attributes including any secret (hidden) exits.
     *
     * @param lockedExits       map of direction to required key item id for exits that start locked
     * @param minLevel          the recommended/minimum level to enter this room, or {@code null} if none
     * @param nightDescription  the description shown at night instead of {@code description}, or
     *                          {@code null} if the room looks the same at night
     * @param lightLevel        the ambient light level ({@code 0} pitch dark, {@code 1} dim,
     *                          {@code 2}+ lit), or {@code null} for a naturally lit room
     * @param outdoor           {@code true} if the room is exposed to the sky and subject to weather
     * @param ambientMessages   the pool of atmospheric flavour lines the room emits occasionally
     * @param hiddenExits       map of direction to destination room id for secret exits that are
     *                          hidden from the room's exit listing until discovered via SEARCH
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
        @Nullable Integer lightLevel,
        boolean outdoor,
        List<String> ambientMessages,
        Map<Direction, RoomId> hiddenExits
    ) {
        this(id, name, description, exits, items, occupants, lockedExits, minLevel, nightDescription,
            lightLevel, outdoor, ambientMessages, hiddenExits, null);
    }

    /**
     * Constructs a room with all optional attributes including a standing environmental hazard.
     *
     * @param lockedExits       map of direction to required key item id for exits that start locked
     * @param minLevel          the recommended/minimum level to enter this room, or {@code null} if none
     * @param nightDescription  the description shown at night instead of {@code description}, or
     *                          {@code null} if the room looks the same at night
     * @param lightLevel        the ambient light level ({@code 0} pitch dark, {@code 1} dim,
     *                          {@code 2}+ lit), or {@code null} for a naturally lit room
     * @param outdoor           {@code true} if the room is exposed to the sky and subject to weather
     * @param ambientMessages   the pool of atmospheric flavour lines the room emits occasionally
     * @param hiddenExits       map of direction to destination room id for secret exits that are
     *                          hidden from the room's exit listing until discovered via SEARCH
     * @param hazard            the standing environmental hazard, or {@code null} for inert terrain
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
        @Nullable Integer lightLevel,
        boolean outdoor,
        List<String> ambientMessages,
        Map<Direction, RoomId> hiddenExits,
        @Nullable RoomHazard hazard
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
        this.outdoor = outdoor;
        this.ambientMessages = List.copyOf(Objects.requireNonNull(ambientMessages, "Ambient messages are required"));
        this.hiddenExits = Map.copyOf(Objects.requireNonNull(hiddenExits, "Hidden exits map is required"));
        this.hazard = hazard;
    }

    /**
     * Returns whether this room declares a standing environmental hazard.
     *
     * @return {@code true} if {@link #getHazard()} is non-null
     */
    public boolean hasHazard() {
        return hazard != null;
    }

    /**
     * Returns whether this room declares any secret (hidden) exits.
     *
     * @return {@code true} if {@link #getHiddenExits()} is non-empty
     */
    public boolean hasHiddenExits() {
        return !hiddenExits.isEmpty();
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

    /**
     * Returns whether this room has any atmospheric flavour lines to emit.
     *
     * @return {@code true} if {@link #getAmbientMessages()} is non-empty
     */
    public boolean hasAmbientMessages() {
        return !ambientMessages.isEmpty();
    }
}

package io.taanielo.jmud.core.world.area;

import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.world.RoomId;

/**
 * A named region of the world: an explicit set of rooms, the areas it borders, and the hand-drawn
 * ASCII map players see when they READ a map item bound to this area.
 *
 * <p>Areas are pure data (loaded from {@code data/areas/*.json}); they carry no player position and
 * no runtime state. Every room in the world must belong to exactly one area — enforced by {@code
 * --validate-data} (issue #529).
 *
 * @param id          the area's stable id
 * @param name        the area's human-readable name
 * @param roomIds     the ids of every room that belongs to this area (never empty)
 * @param connections the ids of the areas directly reachable from this one, used to draw the atlas
 * @param asciiMap    the hand-authored map art as one string per line (never empty, never contains
 *                    a player marker)
 * @param levelRange  the recommended character-level band, surfaced to players so they can judge a
 *                    zone's difficulty before travelling there (issue #550)
 */
public record Area(
    AreaId id,
    String name,
    List<RoomId> roomIds,
    List<AreaId> connections,
    List<String> asciiMap,
    LevelRange levelRange) {

    /** Canonical constructor validating required fields and defensively copying the lists. */
    public Area {
        Objects.requireNonNull(id, "Area id is required");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Area name must not be blank");
        }
        Objects.requireNonNull(levelRange, "Area level range is required");
        roomIds = List.copyOf(Objects.requireNonNull(roomIds, "Area room ids are required"));
        if (roomIds.isEmpty()) {
            throw new IllegalArgumentException("Area " + id.getValue() + " must contain at least one room");
        }
        connections = List.copyOf(Objects.requireNonNull(connections, "Area connections are required"));
        asciiMap = List.copyOf(Objects.requireNonNull(asciiMap, "Area ascii map is required"));
        if (asciiMap.isEmpty()) {
            throw new IllegalArgumentException("Area " + id.getValue() + " must have ascii map art");
        }
    }
}

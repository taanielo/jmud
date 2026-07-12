package io.taanielo.jmud.core.world.area;

import java.util.List;
import java.util.Objects;

/**
 * The world overview rendered when a player READs a World Atlas item: a single hand-drawn ASCII
 * graph of every area and how they connect. Like an {@link Area} it holds no rooms and no player
 * position (issue #529).
 *
 * @param id       the atlas id (matches the {@code map_area_id} of the World Atlas item)
 * @param name     the atlas's human-readable name
 * @param asciiMap the hand-authored overview art as one string per line (never empty)
 */
public record WorldAtlas(String id, String name, List<String> asciiMap) {

    /** Canonical constructor validating required fields and defensively copying the art. */
    public WorldAtlas {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Atlas id must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Atlas name must not be blank");
        }
        asciiMap = List.copyOf(Objects.requireNonNull(asciiMap, "Atlas ascii map is required"));
        if (asciiMap.isEmpty()) {
            throw new IllegalArgumentException("Atlas must have ascii map art");
        }
    }
}

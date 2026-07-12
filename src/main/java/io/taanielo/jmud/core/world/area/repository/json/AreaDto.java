package io.taanielo.jmud.core.world.area.repository.json;

import java.util.List;

/**
 * Serialized shape of a {@code data/areas/<area-id>.json} file.
 *
 * @param schemaVersion the data schema version
 * @param id            the area id
 * @param name          the area name
 * @param roomIds       the ids of rooms belonging to the area
 * @param connections   the ids of adjacent areas
 * @param asciiMap      the hand-drawn map art, one entry per line
 */
public record AreaDto(
    int schemaVersion,
    String id,
    String name,
    List<String> roomIds,
    List<String> connections,
    List<String> asciiMap
) {
}

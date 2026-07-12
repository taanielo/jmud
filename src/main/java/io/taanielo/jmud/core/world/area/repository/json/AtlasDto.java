package io.taanielo.jmud.core.world.area.repository.json;

import java.util.List;

/**
 * Serialized shape of the {@code data/areas/atlas.json} world-overview file.
 *
 * @param schemaVersion the data schema version
 * @param id            the atlas id (matches the World Atlas item's {@code map_area_id})
 * @param name          the atlas name
 * @param asciiMap      the hand-drawn overview art, one entry per line
 */
public record AtlasDto(
    int schemaVersion,
    String id,
    String name,
    List<String> asciiMap
) {
}

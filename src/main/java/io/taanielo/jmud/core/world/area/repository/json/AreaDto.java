package io.taanielo.jmud.core.world.area.repository.json;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Serialized shape of a {@code data/areas/<area-id>.json} file.
 *
 * @param schemaVersion the data schema version
 * @param id            the area id
 * @param name          the area name
 * @param roomIds       the ids of rooms belonging to the area
 * @param connections   the ids of adjacent areas
 * @param asciiMap      the hand-drawn map art, one entry per line
 * @param levelRange    the recommended character-level band for the area (issue #550)
 */
public record AreaDto(
    int schemaVersion,
    String id,
    String name,
    List<String> roomIds,
    List<String> connections,
    List<String> asciiMap,
    @Nullable LevelRangeDto levelRange
) {

    /**
     * Serialized shape of an area's recommended level band.
     *
     * @param min the lowest recommended level
     * @param max the highest recommended level
     */
    public record LevelRangeDto(int min, int max) {
    }
}

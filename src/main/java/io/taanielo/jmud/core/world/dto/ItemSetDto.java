package io.taanielo.jmud.core.world.dto;

import java.util.List;

/**
 * Persistence DTO for one item-set file (see {@code data/item-sets/}). Field names map to the
 * {@code schema_version}, {@code id}, {@code name}, {@code pieces} and {@code thresholds} JSON keys
 * via the shared snake-case {@link JsonDataMapper} configuration.
 *
 * @param schemaVersion the file's schema version
 * @param id            the set id
 * @param name          the set display name
 * @param pieces        ordered ids of the member items
 * @param thresholds    the set's stat thresholds
 */
public record ItemSetDto(
    int schemaVersion,
    String id,
    String name,
    List<String> pieces,
    List<ItemSetThresholdDto> thresholds
) {
}

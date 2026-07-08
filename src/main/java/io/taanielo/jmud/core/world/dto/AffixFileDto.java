package io.taanielo.jmud.core.world.dto;

import java.util.List;

/**
 * Wire representation of the affix definition file ({@code data/item-affixes.json}): a schema
 * version plus the list of affix definitions.
 *
 * @param schemaVersion the file schema version
 * @param affixes       the affix definitions
 */
public record AffixFileDto(
    int schemaVersion,
    List<AffixDto> affixes
) {
}

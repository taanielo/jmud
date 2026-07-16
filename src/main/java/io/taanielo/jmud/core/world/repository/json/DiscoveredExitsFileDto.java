package io.taanielo.jmud.core.world.repository.json;

import java.util.List;

/**
 * JSON transfer object for the world hidden-exit discovery store
 * ({@code data/world-state/discovered-exits.json}).
 *
 * @param schemaVersion the file schema version
 * @param rooms         one entry per room that has at least one discovered hidden exit (may be null)
 */
record DiscoveredExitsFileDto(
    int schemaVersion,
    List<RoomDiscoveredExitsDto> rooms
) {
}

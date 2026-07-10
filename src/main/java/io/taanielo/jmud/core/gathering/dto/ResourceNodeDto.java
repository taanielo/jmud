package io.taanielo.jmud.core.gathering.dto;

import org.jspecify.annotations.Nullable;

/**
 * JSON transfer object for a resource-node definition file ({@code data/resource-nodes/*.json}).
 *
 * @param schemaVersion   the resource-node schema version
 * @param id              the unique node id
 * @param roomId          the id of the room the node lives in
 * @param yieldItem       the id of the raw-material item produced on a successful harvest
 * @param respawnTicks    the number of ticks a depleted node takes to respawn
 * @param name            the short node label shown in harvest messages (no leading article)
 * @param lookDescription the full sentence shown in the room description while available
 */
public record ResourceNodeDto(
    int schemaVersion,
    @Nullable String id,
    @Nullable String roomId,
    @Nullable String yieldItem,
    @Nullable Integer respawnTicks,
    @Nullable String name,
    @Nullable String lookDescription
) {
}

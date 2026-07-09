package io.taanielo.jmud.core.faction.repository.json;

import org.jspecify.annotations.Nullable;

/**
 * JSON transfer object for a faction definition file ({@code factions/*.json}).
 */
record FactionDto(
    int schemaVersion,
    @Nullable String id,
    @Nullable String name,
    @Nullable String description,
    @Nullable Integer killReputationDelta,
    @Nullable Integer hostileThreshold,
    @Nullable Double priceModifierPerPoint
) {
}

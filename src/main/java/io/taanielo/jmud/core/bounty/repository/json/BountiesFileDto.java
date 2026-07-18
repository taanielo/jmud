package io.taanielo.jmud.core.bounty.repository.json;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * JSON transfer object for the persisted open-bounty file ({@code data/world-state/bounties.json}).
 *
 * @param schemaVersion the file schema version
 * @param bounties      the open bounties (may be null when the file predates any content)
 */
record BountiesFileDto(
    int schemaVersion,
    @Nullable List<BountyDto> bounties
) {
}

package io.taanielo.jmud.core.salvage.dto;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * JSON transfer object for the salvage tier definition file ({@code data/salvage/salvage-tiers.json}).
 *
 * @param schemaVersion the salvage tier schema version
 * @param tiers         the per-rarity salvage yield definitions
 */
public record SalvageTiersDto(int schemaVersion, @Nullable List<SalvageTierDto> tiers) {
}

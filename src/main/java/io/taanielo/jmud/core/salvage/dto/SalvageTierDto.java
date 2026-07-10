package io.taanielo.jmud.core.salvage.dto;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * JSON transfer object for a single rarity tier's salvage yield.
 *
 * @param rarity    the rarity tier identifier (e.g. {@code "common"})
 * @param materials the materials yielded by salvaging an item of this tier
 */
public record SalvageTierDto(@Nullable String rarity, @Nullable List<SalvageMaterialDto> materials) {
}

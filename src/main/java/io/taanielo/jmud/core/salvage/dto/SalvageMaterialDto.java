package io.taanielo.jmud.core.salvage.dto;

import org.jspecify.annotations.Nullable;

/**
 * JSON transfer object for a single material yielded by a salvage tier.
 *
 * @param item     the material item id
 * @param quantity how many of the material the salvage yields; defaults to 1 when omitted
 */
public record SalvageMaterialDto(@Nullable String item, @Nullable Integer quantity) {
}

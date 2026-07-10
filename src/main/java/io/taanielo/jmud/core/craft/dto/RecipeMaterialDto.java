package io.taanielo.jmud.core.craft.dto;

import org.jspecify.annotations.Nullable;

/**
 * JSON transfer object for a single material requirement inside a recipe definition.
 *
 * @param item     the material item id
 * @param quantity how many of the material the recipe consumes; defaults to 1 when omitted
 */
public record RecipeMaterialDto(@Nullable String item, @Nullable Integer quantity) {
}

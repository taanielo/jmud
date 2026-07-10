package io.taanielo.jmud.core.craft;

import java.util.Objects;

import io.taanielo.jmud.core.world.ItemId;

/**
 * A single material requirement of a {@link Recipe}: an item and how many of it the recipe consumes.
 *
 * @param itemId   the id of the required material item
 * @param quantity how many copies of the material the recipe consumes; always positive
 */
public record RecipeMaterial(ItemId itemId, int quantity) {

    public RecipeMaterial {
        Objects.requireNonNull(itemId, "Material item id is required");
        if (quantity <= 0) {
            throw new IllegalArgumentException("Material quantity must be positive");
        }
    }
}

package io.taanielo.jmud.core.salvage;

import java.util.Objects;

import io.taanielo.jmud.core.world.ItemId;

/**
 * A single material yielded by salvaging an item of a given {@link SalvageTier}: an item and how many
 * copies of it the player receives.
 *
 * @param itemId   the id of the yielded material item
 * @param quantity how many copies of the material the salvage yields; always positive
 */
public record SalvageMaterial(ItemId itemId, int quantity) {

    public SalvageMaterial {
        Objects.requireNonNull(itemId, "Material item id is required");
        if (quantity <= 0) {
            throw new IllegalArgumentException("Material quantity must be positive");
        }
    }
}

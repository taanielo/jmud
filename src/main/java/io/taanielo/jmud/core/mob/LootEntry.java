package io.taanielo.jmud.core.mob;

import io.taanielo.jmud.core.world.ItemId;

/**
 * Defines an item that may drop from a mob on death, with a probability.
 */
public record LootEntry(ItemId itemId, double dropChance) {

    public LootEntry {
        if (dropChance < 0.0 || dropChance > 1.0) {
            throw new IllegalArgumentException("Drop chance must be between 0.0 and 1.0");
        }
    }
}

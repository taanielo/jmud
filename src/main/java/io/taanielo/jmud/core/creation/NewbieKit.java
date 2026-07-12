package io.taanielo.jmud.core.creation;

import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.world.ItemId;

/**
 * The starting kit granted once to every brand-new character when character creation completes.
 *
 * <p>It bootstraps the early-game economy: a small gold purse plus a few provisions so a fresh
 * level-1 character is not simultaneously broke, hungry and thirsty while the hunger/thirst system
 * decays every tick and its only cure (food/water) costs gold. Values are data-driven
 * ({@code data/newbie-kit.json}); this record is an immutable domain snapshot of that definition.
 *
 * <p>{@code itemIds} is the flattened list of items to place in the new character's inventory — a
 * quantity of {@code 2} in the source data appears as two entries here.
 *
 * @param startingGold the gold added to the new character's purse; never negative
 * @param itemIds      the items to add to the new character's inventory, one entry per copy
 */
public record NewbieKit(int startingGold, List<ItemId> itemIds) {

    /** A kit that grants nothing, used as a safe fallback when no kit data is configured. */
    public static final NewbieKit EMPTY = new NewbieKit(0, List.of());

    /**
     * Canonical constructor.
     *
     * @param startingGold the gold added to the new character's purse; must not be negative
     * @param itemIds      the items to add to the new character's inventory; must not be null
     */
    public NewbieKit {
        if (startingGold < 0) {
            throw new IllegalArgumentException("Starting gold must not be negative: " + startingGold);
        }
        itemIds = List.copyOf(Objects.requireNonNull(itemIds, "Item ids are required"));
    }
}

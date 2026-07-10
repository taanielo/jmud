package io.taanielo.jmud.core.shop;

import java.util.Objects;

import io.taanielo.jmud.core.world.ItemId;

/**
 * A single line in a shop's stock list.
 *
 * <p>{@code price} is optional; when {@code null} the shop uses the item's own
 * {@code value} field as the buy price.
 *
 * <p>{@code minReputation} is an optional reputation gate. When non-{@code null} the entry is only
 * purchasable by a player whose standing with the shop's faction is at least this value; below it the
 * entry is shown as locked and cannot be bought. When {@code null} the entry is always available,
 * preserving the historical (ungated) behaviour.
 */
public record StockEntry(ItemId itemId, Integer price, Integer minReputation) {

    public StockEntry {
        Objects.requireNonNull(itemId, "itemId is required");
    }

    /**
     * Convenience constructor for an ungated stock entry (no minimum reputation requirement).
     *
     * @param itemId the stocked item id
     * @param price  the explicit buy price, or {@code null} to use the item's base value
     */
    public StockEntry(ItemId itemId, Integer price) {
        this(itemId, price, null);
    }
}

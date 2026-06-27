package io.taanielo.jmud.core.shop;

import java.util.Objects;

import io.taanielo.jmud.core.world.ItemId;

/**
 * A single line in a shop's stock list.
 *
 * <p>{@code price} is optional; when {@code null} the shop uses the item's own
 * {@code value} field as the buy price.
 */
public record StockEntry(ItemId itemId, Integer price) {

    public StockEntry {
        Objects.requireNonNull(itemId, "itemId is required");
    }
}

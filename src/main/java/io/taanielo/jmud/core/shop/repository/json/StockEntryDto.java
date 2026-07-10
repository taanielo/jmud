package io.taanielo.jmud.core.shop.repository.json;

/**
 * JSON transfer object for a single stock entry inside a shop definition.
 *
 * <p>{@code minReputation} is optional (snake_case {@code min_reputation}); when absent the entry is
 * always purchasable, matching pre-gating behaviour.
 */
record StockEntryDto(String itemId, Integer price, Integer minReputation) {
}

package io.taanielo.jmud.core.shop.repository.json;

/**
 * JSON transfer object for a single stock entry inside a shop definition.
 */
record StockEntryDto(String itemId, Integer price) {
}

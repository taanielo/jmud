package io.taanielo.jmud.core.shop.repository.json;

import java.util.List;

/**
 * JSON transfer object for a shop definition file ({@code shop.*.json}).
 */
record ShopDto(
    int schemaVersion,
    String id,
    String name,
    String roomId,
    List<StockEntryDto> stock,
    Double sellRatio
) {
}

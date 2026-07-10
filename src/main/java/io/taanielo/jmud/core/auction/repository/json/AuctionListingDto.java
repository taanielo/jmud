package io.taanielo.jmud.core.auction.repository.json;

import io.taanielo.jmud.core.world.dto.ItemDto;

/**
 * JSON transfer object for a single persisted auction listing.
 */
record AuctionListingDto(
    String seller,
    ItemDto item,
    int price,
    String roomId,
    long createdTick,
    long expiryTick
) {
}

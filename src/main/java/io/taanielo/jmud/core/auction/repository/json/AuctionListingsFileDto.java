package io.taanielo.jmud.core.auction.repository.json;

import java.util.List;

/**
 * JSON transfer object for the persisted auction listings file ({@code data/auctions/listings.json}).
 */
record AuctionListingsFileDto(
    int schemaVersion,
    List<AuctionListingDto> listings
) {
}

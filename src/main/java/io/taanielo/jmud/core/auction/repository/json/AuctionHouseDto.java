package io.taanielo.jmud.core.auction.repository.json;

/**
 * JSON transfer object for an Auction House definition file
 * ({@code data/auctions/auction-house.*.json}).
 */
record AuctionHouseDto(
    int schemaVersion,
    String id,
    String auctioneer,
    String roomId
) {
}

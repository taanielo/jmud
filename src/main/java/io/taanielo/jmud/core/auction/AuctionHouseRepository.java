package io.taanielo.jmud.core.auction;

import java.util.List;
import java.util.Optional;

import io.taanielo.jmud.core.world.RoomId;

/**
 * Data-access contract for {@link AuctionHouse} definitions (the static configuration naming the
 * auctioneer and the room the Auction House occupies).
 */
public interface AuctionHouseRepository {

    /**
     * Returns all Auction House definitions.
     *
     * @throws AuctionRepositoryException when data cannot be read
     */
    List<AuctionHouse> findAll() throws AuctionRepositoryException;

    /**
     * Returns the Auction House whose {@code roomId} matches the given room, or empty when there is
     * no Auction House in that room.
     *
     * @throws AuctionRepositoryException when data cannot be read
     */
    Optional<AuctionHouse> findByRoomId(RoomId roomId) throws AuctionRepositoryException;
}

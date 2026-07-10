package io.taanielo.jmud.core.auction;

import java.util.List;

/**
 * Data-access contract for the dynamic set of active {@link AuctionListing}s.
 *
 * <p>The whole set is read and rewritten atomically: {@link AuctionService} computes the new list
 * (adding, removing, or expiring listings) and hands it to {@link #save(List)}. This avoids any
 * ambiguity around removing a specific listing by value equality.
 */
public interface AuctionRepository {

    /**
     * Returns all persisted listings, in insertion order.
     *
     * @throws AuctionRepositoryException when data cannot be read
     */
    List<AuctionListing> findAll() throws AuctionRepositoryException;

    /**
     * Replaces the persisted set of listings with the given list.
     *
     * @param listings the complete new set of active listings
     * @throws AuctionRepositoryException when data cannot be written
     */
    void save(List<AuctionListing> listings) throws AuctionRepositoryException;
}

package io.taanielo.jmud.core.bounty;

import java.util.List;

/**
 * Data-access port for the dynamic set of open {@link Bounty} records.
 *
 * <p>Modelled on {@code AuctionRepository}: the whole set is read and rewritten atomically —
 * {@link BountyService} computes the new list (adding a stake, refunding a cancel, or closing a paid
 * bounty) and hands it to {@link #save(List)}, avoiding any ambiguity around removing a specific
 * entry by value equality.
 *
 * <p>Unlike the auction ledger, {@link #findAll()} must be answerable <em>without</em> disk I/O:
 * the payout check runs on the tick thread for every mob death (AGENTS.md §5). Implementations
 * therefore hold the authoritative set in memory and persist changes write-behind.
 */
public interface BountyRepository {

    /**
     * Returns every open bounty from the in-memory snapshot, in insertion order. Never performs disk
     * I/O, so it is safe to call from the tick-thread mob-death path.
     *
     * @return the open bounties (never {@code null})
     */
    List<Bounty> findAll();

    /**
     * Replaces the set of open bounties with the given list. Implementations update their in-memory
     * snapshot synchronously and flush to durable storage write-behind.
     *
     * @param bounties the complete new set of open bounties
     */
    void save(List<Bounty> bounties);
}

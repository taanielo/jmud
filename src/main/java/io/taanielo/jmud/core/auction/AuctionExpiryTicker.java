package io.taanielo.jmud.core.auction;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.tick.Tickable;

/**
 * World-level {@link Tickable} that returns expired auction listings to their sellers.
 *
 * <p>On each tick it asks {@link AuctionService#expireListings(long)} for the listings that have
 * passed their expiry tick, then for each one resolves the seller — online or offline — and applies
 * {@link AuctionService#applyExpiredReturn(Player, AuctionListing, long)}, which returns the item to
 * the seller's inventory and appends a {@code MAIL} notification. Persisting that update (to a live
 * session or to disk for an offline player) is delegated to the injected {@link SellerUpdate}, using
 * the same cross-player update path {@code MAIL} uses (AGENTS.md §5 — all mutation on the tick
 * thread, no new blocking I/O beyond the existing synchronous save convention).
 */
@Slf4j
public class AuctionExpiryTicker implements Tickable {

    /** Resolves the current {@link Player} for a seller, live in-session or freshly loaded. */
    @FunctionalInterface
    public interface SellerLookup {
        Optional<Player> find(Username seller);
    }

    /** Persists an updated seller wherever they currently are (live session or on disk). */
    @FunctionalInterface
    public interface SellerUpdate {
        void apply(Player updated);
    }

    private final AuctionService auctionService;
    private final LongSupplier currentTick;
    private final SellerLookup sellerLookup;
    private final SellerUpdate sellerUpdate;

    /**
     * Creates the expiry ticker.
     *
     * @param auctionService the service whose listings are expired and returned
     * @param currentTick    supplies the current game tick
     * @param sellerLookup   resolves a seller's current player state (online or offline)
     * @param sellerUpdate   persists the returned-to seller wherever they are
     */
    public AuctionExpiryTicker(
        AuctionService auctionService,
        LongSupplier currentTick,
        SellerLookup sellerLookup,
        SellerUpdate sellerUpdate) {
        this.auctionService = Objects.requireNonNull(auctionService, "auctionService is required");
        this.currentTick = Objects.requireNonNull(currentTick, "currentTick is required");
        this.sellerLookup = Objects.requireNonNull(sellerLookup, "sellerLookup is required");
        this.sellerUpdate = Objects.requireNonNull(sellerUpdate, "sellerUpdate is required");
    }

    /**
     * Expires stale listings and returns each expired item to its seller. Must only be called on the
     * tick thread.
     */
    @Override
    public void tick() {
        long tick = currentTick.getAsLong();
        List<AuctionListing> expired = auctionService.expireListings(tick);
        for (AuctionListing listing : expired) {
            Player seller = sellerLookup.find(listing.seller()).orElse(null);
            if (seller == null) {
                log.warn("Cannot return expired auction item {} — seller {} not found",
                    listing.item().getName(), listing.seller().getValue());
                continue;
            }
            Player updated = auctionService.applyExpiredReturn(seller, listing, tick);
            sellerUpdate.apply(updated);
        }
    }
}

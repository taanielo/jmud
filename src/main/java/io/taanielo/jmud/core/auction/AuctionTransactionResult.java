package io.taanielo.jmud.core.auction;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.player.Player;

/**
 * Outcome of an {@link AuctionService} sell, buy, or cancel operation.
 *
 * <p>On success {@link #updatedActor()} holds the invoking player with their inventory and/or gold
 * updated, and {@link #listing()} holds the listing that was created, bought, or cancelled (so the
 * caller can, for a buy, credit the — possibly offline — seller and notify them). On failure both
 * are {@code null}.
 */
public record AuctionTransactionResult(
    boolean success,
    String message,
    @Nullable Player updatedActor,
    @Nullable AuctionListing listing
) {

    /** Constructs a successful result with the updated invoking player and the affected listing. */
    public static AuctionTransactionResult success(String message, Player updatedActor, AuctionListing listing) {
        return new AuctionTransactionResult(true, message, updatedActor, listing);
    }

    /** Constructs a failure result with no state change. */
    public static AuctionTransactionResult failure(String message) {
        return new AuctionTransactionResult(false, message, null, null);
    }
}

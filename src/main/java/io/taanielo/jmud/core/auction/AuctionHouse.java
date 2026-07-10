package io.taanielo.jmud.core.auction;

import java.util.Objects;

import io.taanielo.jmud.core.world.RoomId;

/**
 * Immutable definition of an Auction House loaded from data files.
 *
 * <p>An Auction House is associated with a single room via {@link #roomId()} (the same room-scoped
 * pattern as {@link io.taanielo.jmud.core.bank.Bank}). Players may only use the {@code AUCTION}
 * command while standing in that room. The {@link #auctioneer()} is a flavour name shown to players;
 * it is not backed by a live mob.
 */
public record AuctionHouse(
    String id,
    String auctioneer,
    RoomId roomId
) {
    public AuctionHouse {
        Objects.requireNonNull(id, "Auction house id is required");
        Objects.requireNonNull(auctioneer, "Auctioneer name is required");
        Objects.requireNonNull(roomId, "Auction house roomId is required");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Auction house id must not be blank");
        }
        if (auctioneer.isBlank()) {
            throw new IllegalArgumentException("Auctioneer name must not be blank");
        }
    }
}

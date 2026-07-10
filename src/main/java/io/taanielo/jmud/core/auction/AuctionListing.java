package io.taanielo.jmud.core.auction;

import java.util.Objects;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.RoomId;

/**
 * Immutable value object describing a single active auction listing: an {@link Item} a
 * {@link #seller() seller} has put up for a fixed gold {@link #price()} at the Auction House in
 * {@link #roomId()}.
 *
 * <p>Listings carry the tick at which they were created and the tick at which they expire; the
 * {@link AuctionExpiryTicker} returns an item to its seller once the current tick reaches
 * {@link #expiryTick()}.
 */
public record AuctionListing(
    Username seller,
    Item item,
    int price,
    RoomId roomId,
    long createdTick,
    long expiryTick
) {
    public AuctionListing {
        Objects.requireNonNull(seller, "seller is required");
        Objects.requireNonNull(item, "item is required");
        Objects.requireNonNull(roomId, "roomId is required");
        if (price <= 0) {
            throw new IllegalArgumentException("price must be positive");
        }
    }

    /**
     * Returns whether this listing has expired as of the given tick.
     *
     * @param currentTick the current game tick
     * @return {@code true} when {@code currentTick} has reached or passed {@link #expiryTick()}
     */
    public boolean isExpired(long currentTick) {
        return currentTick >= expiryTick;
    }

    /**
     * Returns the number of ticks remaining before this listing expires, never negative.
     *
     * @param currentTick the current game tick
     * @return ticks until expiry, or {@code 0} if already expired
     */
    public long ticksRemaining(long currentTick) {
        return Math.max(0, expiryTick - currentTick);
    }
}

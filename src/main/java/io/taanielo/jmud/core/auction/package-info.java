/**
 * Player-to-player asynchronous trading via an Auction House.
 *
 * <p>Sellers list surplus items for a gold price at the Auction House room; any player may buy a
 * listing later, with gold reaching the seller even while they are offline (mirroring the offline
 * delivery path used by {@code MAIL}). Listings expire after a configurable number of ticks and are
 * returned to their sellers. All gold and item mutations happen on the tick thread as part of the
 * invoking player's queued command or the {@link io.taanielo.jmud.core.auction.AuctionExpiryTicker}
 * (AGENTS.md §5); no gold is ever created or destroyed.
 */
@NullMarked
package io.taanielo.jmud.core.auction;

import org.jspecify.annotations.NullMarked;

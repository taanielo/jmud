package io.taanielo.jmud.core.auction;

import io.taanielo.jmud.core.config.GameConfig;

/**
 * Configuration for the Auction House, read from {@code jmud.properties}.
 *
 * <p>Analogous to {@link io.taanielo.jmud.core.player.DeathSettings}; values are expressed in game
 * ticks so behaviour is deterministic and independent of wall-clock time (AGENTS.md §5).
 */
public final class AuctionSettings {

    /** Default number of ticks a listing remains active before it expires and is returned. */
    public static final int DEFAULT_LISTING_TICKS = 2000;

    private static final GameConfig CONFIG = GameConfig.load();

    private AuctionSettings() {
    }

    /**
     * Returns the number of ticks a new listing stays active before expiring.
     *
     * @return the configured listing lifetime in ticks; must be positive
     */
    public static int listingTicks() {
        int ticks = CONFIG.getInt("jmud.auction.listing_ticks", DEFAULT_LISTING_TICKS);
        if (ticks <= 0) {
            throw new IllegalArgumentException("Auction listing ticks must be positive");
        }
        return ticks;
    }
}

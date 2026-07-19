package io.taanielo.jmud.core.bounty;

import io.taanielo.jmud.core.config.GameConfig;

/**
 * Configuration for player-funded mob bounties, read from {@code jmud.properties}.
 *
 * <p>Analogous to {@link io.taanielo.jmud.core.auction.AuctionSettings}; the lifespan is expressed in
 * game ticks so expiry is deterministic and independent of wall-clock time (AGENTS.md §5). Both values
 * may be overridden via {@code jmud.properties} or {@code -Djmud.bounty.*} system properties.
 */
public final class BountySettings {

    /** Default ticks a posted bounty stays open before it expires and the stake is refunded. */
    public static final int DEFAULT_EXPIRY_TICKS = 6000;

    /** Default maximum number of concurrent open bounties a single poster may hold. */
    public static final int DEFAULT_MAX_OPEN_PER_PLAYER = 5;

    private static final GameConfig CONFIG = GameConfig.load();

    private BountySettings() {
    }

    /**
     * Returns the number of ticks a posted bounty stays open before it automatically expires and its
     * full stake is refunded to the poster.
     *
     * @return the configured bounty lifespan in ticks; must be positive
     */
    public static int expiryTicks() {
        int ticks = CONFIG.getInt("jmud.bounty.expiry_ticks", DEFAULT_EXPIRY_TICKS);
        if (ticks <= 0) {
            throw new IllegalArgumentException("Bounty expiry ticks must be positive");
        }
        return ticks;
    }

    /**
     * Returns the maximum number of concurrent open bounties a single poster may hold at once.
     *
     * @return the configured per-poster open-bounty cap; must be positive
     */
    public static int maxOpenPerPlayer() {
        int cap = CONFIG.getInt("jmud.bounty.max_open_per_player", DEFAULT_MAX_OPEN_PER_PLAYER);
        if (cap <= 0) {
            throw new IllegalArgumentException("Bounty max open per player must be positive");
        }
        return cap;
    }
}

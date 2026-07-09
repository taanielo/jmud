package io.taanielo.jmud.core.server.socket;

import io.taanielo.jmud.core.config.GameConfig;

/**
 * Configuration for the "linkdead" grace period: how long a player's session survives in the
 * world after their connection unexpectedly drops, so a reconnecting client can reattach to the
 * live session instead of reloading from disk (issue #343).
 *
 * <p>Values are read once from {@link GameConfig} following the same static-settings pattern as
 * {@link io.taanielo.jmud.core.player.DeathSettings} and
 * {@link io.taanielo.jmud.core.tick.TickSettings}.
 */
public final class LinkdeadSettings {

    /** Default number of ticks a dropped session lingers before it is reaped. */
    public static final int DEFAULT_TIMEOUT_TICKS = 30;

    private static final GameConfig CONFIG = GameConfig.load();

    private LinkdeadSettings() {
    }

    /**
     * Returns whether the linkdead grace period is enabled. When disabled, a dropped connection
     * tears the session down immediately (the pre-#343 behaviour).
     *
     * @return {@code true} when dropped sessions should linger as linkdead
     */
    public static boolean enabled() {
        return CONFIG.getBoolean("jmud.linkdead.enabled", true);
    }

    /**
     * Returns the number of ticks a linkdead session survives before it is saved and reaped.
     *
     * @return a positive tick count
     * @throws IllegalArgumentException if the configured value is not positive
     */
    public static int timeoutTicks() {
        int ticks = CONFIG.getInt("jmud.linkdead.timeout_ticks", DEFAULT_TIMEOUT_TICKS);
        if (ticks <= 0) {
            throw new IllegalArgumentException("Linkdead timeout ticks must be positive");
        }
        return ticks;
    }
}

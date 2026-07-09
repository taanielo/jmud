package io.taanielo.jmud.core.world;

import io.taanielo.jmud.core.config.GameConfig;

/**
 * Configuration for {@link AmbientMessageEngine}: the minimum and maximum number of ticks between
 * consecutive atmospheric flavour emissions in any one room. Follows the same
 * config-key-with-default pattern as {@code WeatherSettings}.
 */
public final class AmbientMessageSettings {

    /** Default minimum number of ticks between ambient emissions in a room. */
    public static final int DEFAULT_MIN_INTERVAL_TICKS = 8;

    /** Default maximum number of ticks between ambient emissions in a room. */
    public static final int DEFAULT_MAX_INTERVAL_TICKS = 15;

    private static final GameConfig CONFIG = GameConfig.load();

    private AmbientMessageSettings() {
    }

    /**
     * Returns the minimum ticks between ambient emissions, read from
     * {@code jmud.ambient.min_interval_ticks} (defaulting to {@link #DEFAULT_MIN_INTERVAL_TICKS}).
     */
    public static int minIntervalTicks() {
        int ticks = CONFIG.getInt("jmud.ambient.min_interval_ticks", DEFAULT_MIN_INTERVAL_TICKS);
        if (ticks <= 0) {
            throw new IllegalArgumentException("Ambient minimum interval must be positive");
        }
        return ticks;
    }

    /**
     * Returns the maximum ticks between ambient emissions, read from
     * {@code jmud.ambient.max_interval_ticks} (defaulting to {@link #DEFAULT_MAX_INTERVAL_TICKS}).
     */
    public static int maxIntervalTicks() {
        int ticks = CONFIG.getInt("jmud.ambient.max_interval_ticks", DEFAULT_MAX_INTERVAL_TICKS);
        if (ticks < minIntervalTicks()) {
            throw new IllegalArgumentException("Ambient maximum interval must be >= minimum interval");
        }
        return ticks;
    }
}

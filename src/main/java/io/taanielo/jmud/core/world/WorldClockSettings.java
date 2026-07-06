package io.taanielo.jmud.core.world;

import io.taanielo.jmud.core.config.GameConfig;

/**
 * Configuration for {@link WorldClock}'s day/night period length. Follows the same
 * config-key-with-default pattern as {@code DeathSettings}.
 */
public final class WorldClockSettings {

    public static final int DEFAULT_TICKS_PER_PHASE = 50;

    private static final GameConfig CONFIG = GameConfig.load();

    private WorldClockSettings() {
    }

    /**
     * Returns the number of ticks each day/night phase lasts, read from
     * {@code jmud.world.ticks_per_phase} (defaulting to {@link #DEFAULT_TICKS_PER_PHASE}).
     */
    public static int ticksPerPhase() {
        int ticks = CONFIG.getInt("jmud.world.ticks_per_phase", DEFAULT_TICKS_PER_PHASE);
        if (ticks <= 0) {
            throw new IllegalArgumentException("Ticks per phase must be positive");
        }
        return ticks;
    }
}

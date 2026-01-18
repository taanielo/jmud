package io.taanielo.jmud.core.tick;

import io.taanielo.jmud.core.config.GameConfig;

public final class TickSettings {
    public static final long DEFAULT_INTERVAL_MS = 500;

    private static final GameConfig CONFIG = GameConfig.load();

    private TickSettings() {
    }

    public static long intervalMillis() {
        long interval = CONFIG.getLong("jmud.tick.interval.ms", DEFAULT_INTERVAL_MS);
        if (interval <= 0) {
            throw new IllegalArgumentException("Tick interval must be positive");
        }
        return interval;
    }
}

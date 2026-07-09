package io.taanielo.jmud.core.tick;

import io.taanielo.jmud.core.config.GameConfig;

public final class TickSettings {
    public static final long DEFAULT_INTERVAL_MS = 500;
    public static final int DEFAULT_METRICS_RETENTION = 100;

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

    /**
     * Number of recent ticks {@link TickMetricsService} retains for its rolling window statistics.
     *
     * @return the configured retention count; defaults to {@value #DEFAULT_METRICS_RETENTION}
     */
    public static int metricsRetention() {
        int retention = CONFIG.getInt("jmud.tick.metrics.retention", DEFAULT_METRICS_RETENTION);
        if (retention <= 0) {
            throw new IllegalArgumentException("Tick metrics retention must be positive");
        }
        return retention;
    }
}

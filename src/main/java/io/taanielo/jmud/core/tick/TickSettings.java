package io.taanielo.jmud.core.tick;

public final class TickSettings {
    public static final long DEFAULT_INTERVAL_MS = 500;

    private TickSettings() {
    }

    public static long resolveIntervalMillis() {
        String value = System.getProperty("jmud.tick.interval.ms");
        if (value == null || value.isBlank()) {
            return DEFAULT_INTERVAL_MS;
        }
        try {
            long interval = Long.parseLong(value);
            if (interval <= 0) {
                throw new IllegalArgumentException("Tick interval must be positive");
            }
            return interval;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid tick interval value: " + value, e);
        }
    }
}

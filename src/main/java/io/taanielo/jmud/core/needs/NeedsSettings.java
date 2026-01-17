package io.taanielo.jmud.core.needs;

public final class NeedsSettings {
    public static final boolean DEFAULT_ENABLED = true;
    public static final long DEFAULT_TICK_MILLIS = 1_000L;
    public static final int DEFAULT_MAX = 100;
    public static final int DEFAULT_DECAY_PER_TICK = 1;
    public static final int DEFAULT_WARNING_THRESHOLD = 30;
    public static final int DEFAULT_SEVERE_THRESHOLD = 10;
    public static final int DEFAULT_SEVERE_DAMAGE = 1;
    public static final int DEFAULT_STARTING_HEALTH = 10;

    private NeedsSettings() {
    }

    public static boolean enabled() {
        String value = System.getProperty("jmud.needs.enabled");
        if (value == null || value.isBlank()) {
            return DEFAULT_ENABLED;
        }
        return Boolean.parseBoolean(value);
    }

    public static long tickMillis() {
        return getLong("jmud.needs.tick.ms", DEFAULT_TICK_MILLIS);
    }

    public static int maxHunger() {
        return getInt("jmud.needs.hunger.max", DEFAULT_MAX);
    }

    public static int maxThirst() {
        return getInt("jmud.needs.thirst.max", DEFAULT_MAX);
    }

    public static int hungerDecay() {
        return getInt("jmud.needs.hunger.decay", DEFAULT_DECAY_PER_TICK);
    }

    public static int thirstDecay() {
        return getInt("jmud.needs.thirst.decay", DEFAULT_DECAY_PER_TICK);
    }

    public static int warningThreshold() {
        return getInt("jmud.needs.warning.threshold", DEFAULT_WARNING_THRESHOLD);
    }

    public static int severeThreshold() {
        return getInt("jmud.needs.severe.threshold", DEFAULT_SEVERE_THRESHOLD);
    }

    public static int severeDamage() {
        return getInt("jmud.needs.severe.damage", DEFAULT_SEVERE_DAMAGE);
    }

    public static int startingHealth() {
        return getInt("jmud.needs.health.start", DEFAULT_STARTING_HEALTH);
    }

    private static int getInt(String key, int fallback) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer for " + key + ": " + value, e);
        }
    }

    private static long getLong(String key, long fallback) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid long for " + key + ": " + value, e);
        }
    }
}

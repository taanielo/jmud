package io.taanielo.jmud.core.effects;

public final class EffectSettings {
    public static final boolean DEFAULT_ENABLED = true;
    public static final int DEFAULT_MAX = 100;
    public static final int DEFAULT_DECAY_PER_TICK = 1;
    public static final int DEFAULT_WARNING_THRESHOLD = 30;
    public static final int DEFAULT_SEVERE_THRESHOLD = 10;

    private EffectSettings() {
    }

    public static boolean enabled() {
        String value = System.getProperty("jmud.effects.enabled");
        if (value == null || value.isBlank()) {
            return DEFAULT_ENABLED;
        }
        return Boolean.parseBoolean(value);
    }

    public static int maxHunger() {
        return getInt("jmud.effects.hunger.max", DEFAULT_MAX);
    }

    public static int maxThirst() {
        return getInt("jmud.effects.thirst.max", DEFAULT_MAX);
    }

    public static int hungerDecay() {
        return getInt("jmud.effects.hunger.decay", DEFAULT_DECAY_PER_TICK);
    }

    public static int thirstDecay() {
        return getInt("jmud.effects.thirst.decay", DEFAULT_DECAY_PER_TICK);
    }

    public static int warningThreshold() {
        return getInt("jmud.effects.warning.threshold", DEFAULT_WARNING_THRESHOLD);
    }

    public static int severeThreshold() {
        return getInt("jmud.effects.severe.threshold", DEFAULT_SEVERE_THRESHOLD);
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
}

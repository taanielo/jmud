package io.taanielo.jmud.core.effects;

import io.taanielo.jmud.core.config.GameConfig;

public final class EffectSettings {
    public static final boolean DEFAULT_ENABLED = true;
    public static final int DEFAULT_MAX = 100;
    public static final int DEFAULT_DECAY_PER_TICK = 1;
    public static final int DEFAULT_WARNING_THRESHOLD = 30;
    public static final int DEFAULT_SEVERE_THRESHOLD = 10;

    private static final GameConfig CONFIG = GameConfig.load();

    private EffectSettings() {
    }

    public static boolean enabled() {
        return CONFIG.getBoolean("jmud.effects.enabled", DEFAULT_ENABLED);
    }

    public static int maxHunger() {
        return CONFIG.getInt("jmud.effects.hunger.max", DEFAULT_MAX);
    }

    public static int maxThirst() {
        return CONFIG.getInt("jmud.effects.thirst.max", DEFAULT_MAX);
    }

    public static int hungerDecay() {
        return CONFIG.getInt("jmud.effects.hunger.decay", DEFAULT_DECAY_PER_TICK);
    }

    public static int thirstDecay() {
        return CONFIG.getInt("jmud.effects.thirst.decay", DEFAULT_DECAY_PER_TICK);
    }

    public static int warningThreshold() {
        return CONFIG.getInt("jmud.effects.warning.threshold", DEFAULT_WARNING_THRESHOLD);
    }

    public static int severeThreshold() {
        return CONFIG.getInt("jmud.effects.severe.threshold", DEFAULT_SEVERE_THRESHOLD);
    }
}

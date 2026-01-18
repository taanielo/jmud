package io.taanielo.jmud.core.healing;

import io.taanielo.jmud.core.config.GameConfig;

public final class HealingSettings {
    public static final boolean DEFAULT_ENABLED = true;
    public static final int DEFAULT_BASE_HP_PER_TICK = 1;

    private static final GameConfig CONFIG = GameConfig.load();

    private HealingSettings() {
    }

    public static boolean enabled() {
        return CONFIG.getBoolean("jmud.healing.enabled", DEFAULT_ENABLED);
    }

    public static int baseHpPerTick() {
        int base = CONFIG.getInt("jmud.healing.base_hp_per_tick", DEFAULT_BASE_HP_PER_TICK);
        if (base < 0) {
            throw new IllegalArgumentException("Base healing per tick must be non-negative");
        }
        return base;
    }

}

package io.taanielo.jmud.core.effects;

import io.taanielo.jmud.core.config.GameConfig;

public final class EffectSettings {
    public static final boolean DEFAULT_ENABLED = true;

    private static final GameConfig CONFIG = GameConfig.load();

    private EffectSettings() {
    }

    public static boolean enabled() {
        return CONFIG.getBoolean("jmud.effects.enabled", DEFAULT_ENABLED);
    }
}

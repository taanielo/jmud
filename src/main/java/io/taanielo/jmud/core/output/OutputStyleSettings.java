package io.taanielo.jmud.core.output;

import io.taanielo.jmud.core.config.GameConfig;

public final class OutputStyleSettings {
    private static final boolean DEFAULT_ANSI_ENABLED = false;
    private static final GameConfig CONFIG = GameConfig.load();

    private OutputStyleSettings() {
    }

    public static boolean ansiEnabledByDefault() {
        return CONFIG.getBoolean("jmud.output.ansi.enabled", DEFAULT_ANSI_ENABLED);
    }
}

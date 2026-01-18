package io.taanielo.jmud.core.prompt;

import io.taanielo.jmud.core.config.GameConfig;

public final class PromptSettings {
    public static final String DEFAULT_FORMAT =
        "[{hp}/{maxHp}hp {mana}/{maxMana}mn {move}/{maxMove}mv {exp}xp]";

    private static final GameConfig CONFIG = GameConfig.load();

    private PromptSettings() {
    }

    public static String defaultFormat() {
        return CONFIG.getString("jmud.prompt.format", DEFAULT_FORMAT);
    }
}

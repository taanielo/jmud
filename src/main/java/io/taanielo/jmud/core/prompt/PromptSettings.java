package io.taanielo.jmud.core.prompt;

import io.taanielo.jmud.core.settings.ApplicationSettings;

public final class PromptSettings {
    public static final String DEFAULT_FORMAT =
        "[{hp}/{maxHp}hp {mana}/{maxMana}mn {move}/{maxMove}mv {exp}xp]";

    private PromptSettings() {
    }

    public static String defaultFormat() {
        return ApplicationSettings.getString("jmud.prompt.format", DEFAULT_FORMAT);
    }
}

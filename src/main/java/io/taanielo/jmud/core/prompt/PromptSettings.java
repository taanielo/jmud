package io.taanielo.jmud.core.prompt;

import io.taanielo.jmud.core.settings.ApplicationSettings;

public final class PromptSettings {
    public static final String DEFAULT_FORMAT =
        "HP {hp}/{maxHp}  Mana {mana}/{maxMana}  Move {move}/{maxMove}  Exp {exp}";

    private PromptSettings() {
    }

    public static String defaultFormat() {
        return ApplicationSettings.getString("jmud.prompt.format", DEFAULT_FORMAT);
    }
}

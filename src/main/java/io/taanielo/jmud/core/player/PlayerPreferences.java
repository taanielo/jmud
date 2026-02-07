package io.taanielo.jmud.core.player;

import java.util.Objects;

public class PlayerPreferences {
    private final String promptFormat;
    private final boolean ansiEnabled;

    public PlayerPreferences(String promptFormat, Boolean ansiEnabled) {
        this.promptFormat = Objects.requireNonNull(promptFormat, "Prompt format is required");
        this.ansiEnabled = Objects.requireNonNullElse(ansiEnabled, false);
    }

    public String promptFormat() {
        return promptFormat;
    }

    public boolean ansiEnabled() {
        return ansiEnabled;
    }

    public PlayerPreferences withAnsiEnabled(boolean enabled) {
        return new PlayerPreferences(promptFormat, enabled);
    }

    public PlayerPreferences withPromptFormat(String nextFormat) {
        return new PlayerPreferences(nextFormat, ansiEnabled);
    }
}

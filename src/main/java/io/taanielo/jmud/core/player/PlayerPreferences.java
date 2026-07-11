package io.taanielo.jmud.core.player;

import java.util.Objects;

public class PlayerPreferences {
    private final String promptFormat;
    private final boolean ansiEnabled;
    private final boolean autoLootEnabled;

    public PlayerPreferences(String promptFormat, Boolean ansiEnabled) {
        this(promptFormat, ansiEnabled, false);
    }

    public PlayerPreferences(String promptFormat, Boolean ansiEnabled, Boolean autoLootEnabled) {
        this.promptFormat = Objects.requireNonNull(promptFormat, "Prompt format is required");
        this.ansiEnabled = Objects.requireNonNullElse(ansiEnabled, false);
        this.autoLootEnabled = Objects.requireNonNullElse(autoLootEnabled, false);
    }

    public String promptFormat() {
        return promptFormat;
    }

    public boolean ansiEnabled() {
        return ansiEnabled;
    }

    public boolean autoLootEnabled() {
        return autoLootEnabled;
    }

    public PlayerPreferences withAnsiEnabled(boolean enabled) {
        return new PlayerPreferences(promptFormat, enabled, autoLootEnabled);
    }

    public PlayerPreferences withAutoLootEnabled(boolean enabled) {
        return new PlayerPreferences(promptFormat, ansiEnabled, enabled);
    }

    public PlayerPreferences withPromptFormat(String nextFormat) {
        return new PlayerPreferences(nextFormat, ansiEnabled, autoLootEnabled);
    }
}

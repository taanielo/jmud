package io.taanielo.jmud.core.player;

import java.util.Objects;

public class PlayerPreferences {
    private final String promptFormat;
    private final boolean ansiEnabled;
    private final boolean autoLootEnabled;
    private final boolean briefModeEnabled;
    private final boolean autoAssistEnabled;

    public PlayerPreferences(String promptFormat, Boolean ansiEnabled) {
        this(promptFormat, ansiEnabled, false);
    }

    public PlayerPreferences(String promptFormat, Boolean ansiEnabled, Boolean autoLootEnabled) {
        this(promptFormat, ansiEnabled, autoLootEnabled, false);
    }

    public PlayerPreferences(
            String promptFormat, Boolean ansiEnabled, Boolean autoLootEnabled, Boolean briefModeEnabled) {
        this(promptFormat, ansiEnabled, autoLootEnabled, briefModeEnabled, false);
    }

    public PlayerPreferences(
            String promptFormat,
            Boolean ansiEnabled,
            Boolean autoLootEnabled,
            Boolean briefModeEnabled,
            Boolean autoAssistEnabled) {
        this.promptFormat = Objects.requireNonNull(promptFormat, "Prompt format is required");
        this.ansiEnabled = Objects.requireNonNullElse(ansiEnabled, false);
        this.autoLootEnabled = Objects.requireNonNullElse(autoLootEnabled, false);
        this.briefModeEnabled = Objects.requireNonNullElse(briefModeEnabled, false);
        this.autoAssistEnabled = Objects.requireNonNullElse(autoAssistEnabled, false);
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

    public boolean briefModeEnabled() {
        return briefModeEnabled;
    }

    public boolean autoAssistEnabled() {
        return autoAssistEnabled;
    }

    public PlayerPreferences withAnsiEnabled(boolean enabled) {
        return new PlayerPreferences(promptFormat, enabled, autoLootEnabled, briefModeEnabled, autoAssistEnabled);
    }

    public PlayerPreferences withAutoLootEnabled(boolean enabled) {
        return new PlayerPreferences(promptFormat, ansiEnabled, enabled, briefModeEnabled, autoAssistEnabled);
    }

    public PlayerPreferences withBriefModeEnabled(boolean enabled) {
        return new PlayerPreferences(promptFormat, ansiEnabled, autoLootEnabled, enabled, autoAssistEnabled);
    }

    public PlayerPreferences withAutoAssistEnabled(boolean enabled) {
        return new PlayerPreferences(promptFormat, ansiEnabled, autoLootEnabled, briefModeEnabled, enabled);
    }

    public PlayerPreferences withPromptFormat(String nextFormat) {
        return new PlayerPreferences(nextFormat, ansiEnabled, autoLootEnabled, briefModeEnabled, autoAssistEnabled);
    }
}

package io.taanielo.jmud.core.weather;

import java.util.Objects;
import java.util.Optional;

/**
 * An immutable snapshot of the current weather: its {@link WeatherType} and an {@code intensity}
 * on a 0-100 scale. Intensity drives the wording of the room description and the visibility status,
 * while the combat modifiers are fixed per active type (matching the design in issue #323).
 *
 * <p>A weather snapshot is "active" only when its type is not {@link WeatherType#CLEAR} and its
 * intensity is above zero; a clear or zero-intensity snapshot has no description and no combat
 * effect.
 */
public record Weather(WeatherType type, int intensity) {

    private static final int MIN_INTENSITY = 0;
    private static final int MAX_INTENSITY = 100;
    private static final int LIGHT_THRESHOLD = 34;
    private static final int MODERATE_THRESHOLD = 67;

    public Weather {
        Objects.requireNonNull(type, "Weather type is required");
        intensity = Math.max(MIN_INTENSITY, Math.min(MAX_INTENSITY, intensity));
        if (type == WeatherType.CLEAR) {
            intensity = MIN_INTENSITY;
        }
    }

    /**
     * Returns the neutral, no-weather snapshot.
     */
    public static Weather clear() {
        return new Weather(WeatherType.CLEAR, MIN_INTENSITY);
    }

    /**
     * Returns whether this weather is currently having any effect (non-clear with positive
     * intensity).
     */
    public boolean isActive() {
        return type != WeatherType.CLEAR && intensity > MIN_INTENSITY;
    }

    /**
     * Returns the sentence appended to an outdoor room's look description, or empty when the
     * weather is not active.
     */
    public Optional<String> roomDescription() {
        if (!isActive()) {
            return Optional.empty();
        }
        return Optional.of(switch (type) {
            case RAIN -> tier("A light rain patters down here.",
                "A steady rain falls here.",
                "Heavy rain pours down here.");
            case FOG -> tier("A thin mist drifts through the air.",
                "A bank of fog hangs over everything here.",
                "A thick fog blankets the area, limiting sight.");
            case STORM -> tier("Rain lashes down as a storm gathers here.",
                "A storm rages here, wind howling.",
                "A violent storm tears through here, sight all but lost.");
            case CLEAR -> "";
        });
    }

    /**
     * Returns the attacker accuracy modifier (a hit-chance delta, never positive) contributed by
     * this weather; {@code 0} when not active.
     */
    public int accuracyModifier() {
        if (!isActive()) {
            return 0;
        }
        return switch (type) {
            case RAIN -> -2;
            case FOG -> -5;
            case STORM -> -10;
            case CLEAR -> 0;
        };
    }

    /**
     * Returns the dodge modifier (an additional hit-chance reduction applied against attackers)
     * contributed by this weather; only rain grants one.
     */
    public int dodgeModifier() {
        if (!isActive()) {
            return 0;
        }
        return type == WeatherType.RAIN ? 2 : 0;
    }

    /**
     * Returns the extra hit-chance penalty applied to ranged attacks (on top of
     * {@link #accuracyModifier()}); only storms impose one.
     */
    public int rangedAccuracyModifier() {
        if (!isActive()) {
            return 0;
        }
        return type == WeatherType.STORM ? -15 : 0;
    }

    /**
     * Returns the net hit-chance delta this weather applies to every attack (both the attacker's
     * accuracy penalty and the defender's dodge bonus).
     */
    public int combatHitModifier() {
        return accuracyModifier() - dodgeModifier();
    }

    /**
     * Returns the additional hit-chance delta applied only to ranged attacks.
     */
    public int rangedHitModifier() {
        return rangedAccuracyModifier();
    }

    /**
     * Returns whether this weather reduces visibility (fog or storm while active).
     */
    public boolean affectsVisibility() {
        return isActive() && (type == WeatherType.FOG || type == WeatherType.STORM);
    }

    /**
     * Returns the visibility status line shown in LOOK/WHO/SCORE, or empty when visibility is
     * unaffected.
     */
    public Optional<String> visibilityStatus() {
        if (!affectsVisibility()) {
            return Optional.empty();
        }
        return Optional.of(type == WeatherType.STORM
            ? "Visibility: poor (the storm obscures your sight)."
            : "Visibility: reduced (fog hangs in the air).");
    }

    private String tier(String light, String moderate, String heavy) {
        if (intensity < LIGHT_THRESHOLD) {
            return light;
        }
        if (intensity < MODERATE_THRESHOLD) {
            return moderate;
        }
        return heavy;
    }
}

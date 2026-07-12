package io.taanielo.jmud.core.character;

import java.util.Locale;

/**
 * The deterministic cadence at which a class grants a point in a given attribute as characters level
 * up. A character at level {@code L} has accrued {@code (L - 1) / everyNLevels} points for the
 * attribute, so all gains are zero at level 1 (where the character's profile is baseline plus race
 * and class creation bonuses) and grow lock-step with level — never randomly (AGENTS.md §5).
 */
public enum AttributeGainCadence {

    /** No gains: the attribute never grows from levelling. */
    NONE(0),
    /** One point every level (past level 1). */
    EVERY_LEVEL(1),
    /** One point every two levels. */
    EVERY_2_LEVELS(2),
    /** One point every three levels. */
    EVERY_3_LEVELS(3);

    private final int everyNLevels;

    AttributeGainCadence(int everyNLevels) {
        this.everyNLevels = everyNLevels;
    }

    /**
     * Returns the total number of points this cadence has granted for a character at the given level.
     *
     * @param level the character's current level (1-based)
     * @return the accumulated gain, {@code 0} for {@link #NONE} or level {@code <= 1}
     */
    public int gainAtLevel(int level) {
        if (everyNLevels == 0 || level <= 1) {
            return 0;
        }
        return (level - 1) / everyNLevels;
    }

    /**
     * Parses a cadence from its data string, accepting {@code "every_level"}, {@code "every_2_levels"}
     * and {@code "every_3_levels"} (case-insensitive). A {@code null}, blank or unrecognised value maps
     * to {@link #NONE}.
     *
     * @param value the raw cadence string from class JSON
     * @return the matching cadence, or {@link #NONE} when unset or unrecognised
     */
    public static AttributeGainCadence fromString(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "every_level" -> EVERY_LEVEL;
            case "every_2_levels" -> EVERY_2_LEVELS;
            case "every_3_levels" -> EVERY_3_LEVELS;
            default -> NONE;
        };
    }
}

package io.taanielo.jmud.core.effects;

import java.util.Locale;
import java.util.Optional;

/**
 * Hard crowd-control classification carried by an {@link EffectDefinition}.
 *
 * <p>Whereas an effect's stat modifiers only ever weaken a target (attack/hit/defense
 * penalties), a control classification makes the effect mechanically forbid entire player
 * actions while it is active. The classification is additive: an effect without one behaves
 * exactly as before.
 *
 * <ul>
 *   <li>{@link #ROOT} — the bearer cannot move or flee (e.g. {@code rooted}, {@code shackled}).</li>
 *   <li>{@link #SILENCE} — the bearer cannot voice a spell, but skills still work
 *       (e.g. {@code silenced}, {@code garrote}).</li>
 *   <li>{@link #STUN} — the bearer is fully incapacitated: no move, flee, cast, or skill use
 *       (e.g. {@code hammer-of-justice}).</li>
 * </ul>
 */
public enum ControlType {
    /** Prevents movement and fleeing. */
    ROOT("rooted in place"),
    /** Prevents casting spell-type abilities (skills are unaffected). */
    SILENCE("silenced"),
    /** Fully incapacitates: prevents movement, fleeing, casting, and skill use. */
    STUN("stunned");

    private final String descriptor;

    ControlType(String descriptor) {
        this.descriptor = descriptor;
    }

    /**
     * Returns the short adjectival phrase used to describe a bearer of this control type in
     * player-facing refusal messages (e.g. {@code "rooted in place"} for {@link #ROOT}).
     *
     * @return the descriptor phrase
     */
    public String descriptor() {
        return descriptor;
    }

    /**
     * Parses a control classification from its data-file string (case-insensitive), returning
     * empty for a {@code null} or blank value so that effects without a {@code control} field
     * remain unclassified.
     *
     * @param value the raw string from the effect JSON, may be {@code null} or blank
     * @return the matching control type, or empty when unspecified
     * @throws IllegalArgumentException when a non-blank value does not name a control type
     */
    public static Optional<ControlType> fromString(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        try {
            return Optional.of(ControlType.valueOf(normalized));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown control type " + value);
        }
    }
}

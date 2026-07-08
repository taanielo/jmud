package io.taanielo.jmud.core.player;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Immutable value object tracking a player's hunger and thirst on a 0&ndash;100 scale.
 *
 * <p>{@code 100} represents a fully sated/quenched player and {@code 0} represents a
 * starving/parched one. Both values are always clamped to the {@code [0, 100]} range.
 *
 * <p>When either value drops below {@link #PENALTY_THRESHOLD} the player is considered
 * malnourished and their per-tick regeneration is penalised (see {@link #regenPenalised()}).
 */
public record PlayerSustenance(
    @JsonProperty("hunger") int hunger,
    @JsonProperty("thirst") int thirst
) {

    /** Maximum value for both hunger and thirst (fully sated / quenched). */
    public static final int MAX = 100;

    /** Below this threshold the player is starving/parched and regeneration is penalised. */
    public static final int PENALTY_THRESHOLD = 20;

    /**
     * Canonical constructor that clamps both values into the {@code [0, MAX]} range.
     *
     * @param hunger hunger level; clamped to {@code [0, 100]}
     * @param thirst thirst level; clamped to {@code [0, 100]}
     */
    @JsonCreator
    public PlayerSustenance {
        hunger = clamp(hunger);
        thirst = clamp(thirst);
    }

    /**
     * Returns a fully sated and quenched sustenance state (both values at {@link #MAX}).
     *
     * @return a new full sustenance instance
     */
    public static PlayerSustenance full() {
        return new PlayerSustenance(MAX, MAX);
    }

    /**
     * Returns the default sustenance state for a new or freshly loaded player
     * (fully sated and quenched).
     *
     * @return the default sustenance instance
     */
    public static PlayerSustenance defaults() {
        return full();
    }

    /**
     * Returns a copy with both hunger and thirst reduced by the given amount.
     *
     * @param amount points to decay from each value; must be non-negative
     * @return the decayed sustenance (may be {@code this} when already empty)
     */
    public PlayerSustenance decay(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Decay amount must be non-negative");
        }
        if (amount == 0 || (hunger == 0 && thirst == 0)) {
            return this;
        }
        return new PlayerSustenance(hunger - amount, thirst - amount);
    }

    /**
     * Returns a copy with hunger increased by the given amount (capped at {@link #MAX}).
     *
     * @param amount hunger points to restore; must be non-negative
     * @return the fed sustenance
     */
    public PlayerSustenance feed(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Feed amount must be non-negative");
        }
        return new PlayerSustenance(hunger + amount, thirst);
    }

    /**
     * Returns a copy with thirst increased by the given amount (capped at {@link #MAX}).
     *
     * @param amount thirst points to restore; must be non-negative
     * @return the quenched sustenance
     */
    public PlayerSustenance quench(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Quench amount must be non-negative");
        }
        return new PlayerSustenance(hunger, thirst + amount);
    }

    /**
     * Returns {@code true} when hunger has dropped below {@link #PENALTY_THRESHOLD}.
     */
    @JsonIgnore
    public boolean isStarving() {
        return hunger < PENALTY_THRESHOLD;
    }

    /**
     * Returns {@code true} when thirst has dropped below {@link #PENALTY_THRESHOLD}.
     */
    @JsonIgnore
    public boolean isParched() {
        return thirst < PENALTY_THRESHOLD;
    }

    /**
     * Returns {@code true} when per-tick regeneration should be penalised, i.e. when the
     * player is either starving or parched.
     */
    @JsonIgnore
    public boolean regenPenalised() {
        return isStarving() || isParched();
    }

    private static int clamp(int value) {
        if (value < 0) {
            return 0;
        }
        return Math.min(value, MAX);
    }
}

package io.taanielo.jmud.core.combat.flavor;

import java.util.Objects;

/**
 * One inclusive percentage band of the target-condition table.
 *
 * <p>A tier matches when a combatant's current HP, expressed as a percentage of its maximum, falls in
 * {@code [minPercent, maxPercent]}.
 *
 * @param minPercent  inclusive lower bound (percent of current/max HP)
 * @param maxPercent  inclusive upper bound
 * @param description the condition phrase, e.g. {@code "has quite a few wounds"}
 */
public record TargetConditionTier(int minPercent, int maxPercent, String description) {
    public TargetConditionTier {
        Objects.requireNonNull(description, "Condition description is required");
        if (description.isBlank()) {
            throw new IllegalArgumentException("Condition description must not be blank");
        }
        if (minPercent < 0 || maxPercent > 100) {
            throw new IllegalArgumentException("Condition percent bounds must lie within [0, 100]");
        }
        if (maxPercent < minPercent) {
            throw new IllegalArgumentException(
                "Maximum percent " + maxPercent + " must not be below minimum " + minPercent);
        }
    }

    /**
     * Returns whether the given percentage falls within this tier's inclusive band.
     *
     * @param percent current HP as a percentage of maximum HP
     * @return {@code true} if {@code percent} is in {@code [minPercent, maxPercent]}
     */
    public boolean matches(int percent) {
        return percent >= minPercent && percent <= maxPercent;
    }
}

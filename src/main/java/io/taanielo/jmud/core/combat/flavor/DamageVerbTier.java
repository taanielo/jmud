package io.taanielo.jmud.core.combat.flavor;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * One inclusive percentage band of the damage-verb table.
 *
 * <p>A tier matches when {@code damagePercent} of the target's maximum HP falls in
 * {@code [minPercent, maxPercent]}. A {@code null} {@code maxPercent} marks the open-ended top tier
 * (e.g. {@code >= 100}).
 *
 * @param minPercent inclusive lower bound (percent of target max HP)
 * @param maxPercent inclusive upper bound, or {@code null} for the open-ended top tier
 * @param verb       the verb to render for this band
 */
public record DamageVerbTier(int minPercent, @Nullable Integer maxPercent, DamageVerb verb) {
    public DamageVerbTier {
        Objects.requireNonNull(verb, "Damage verb is required");
        if (minPercent < 0) {
            throw new IllegalArgumentException("Minimum percent must not be negative: " + minPercent);
        }
        if (maxPercent != null && maxPercent < minPercent) {
            throw new IllegalArgumentException(
                "Maximum percent " + maxPercent + " must not be below minimum " + minPercent);
        }
    }

    /**
     * Returns whether the given percentage falls within this tier's inclusive band.
     *
     * @param percent damage as a percentage of the target's maximum HP
     * @return {@code true} if {@code percent} is in {@code [minPercent, maxPercent]}
     */
    public boolean matches(int percent) {
        if (percent < minPercent) {
            return false;
        }
        return maxPercent == null || percent <= maxPercent;
    }
}

package io.taanielo.jmud.core.mob;

/**
 * Support-caster AI profile for a mob that mends wounded allies instead of always attacking
 * (issue #733). A mob template carrying a non-null {@code HealerProfile} is a <em>healer</em>: on its
 * own AI decision, when a different alive, non-pet mob in the same room is wounded at or below
 * {@link #thresholdPercent()} of its maximum HP (and not already at full), the healer spends that
 * decision restoring {@code [healMin, healMax]} HP (clamped to the ally's max) to the most-wounded
 * ally rather than swinging at a player. When no ally needs healing it attacks normally like any
 * other mob.
 *
 * <p>Purely additive mob-side AI: it introduces no new player command and no save-schema change.
 * Healed amounts are rolled through the seeded RNG port so encounters stay deterministic
 * (AGENTS.md §5).
 *
 * @param healMin          minimum HP restored to an ally on a heal; must be positive
 * @param healMax          maximum HP restored to an ally on a heal; must be {@code >= healMin}
 * @param thresholdPercent HP percentage of an ally's maximum, in {@code [1, 100]}, at or below which
 *                         the ally counts as "wounded" and eligible to be healed
 */
public record HealerProfile(int healMin, int healMax, int thresholdPercent) {

    /**
     * Default wounded threshold (as a percent of max HP) used when a healer mob authors none: an ally
     * at or below half health is eligible to be healed.
     */
    public static final int DEFAULT_THRESHOLD_PERCENT = 50;

    public HealerProfile {
        if (healMin <= 0) {
            throw new IllegalArgumentException("HealerProfile healMin must be positive, got " + healMin);
        }
        if (healMax < healMin) {
            throw new IllegalArgumentException(
                "HealerProfile healMax must be >= healMin, got healMin=" + healMin + " healMax=" + healMax);
        }
        if (thresholdPercent < 1 || thresholdPercent > 100) {
            throw new IllegalArgumentException(
                "HealerProfile thresholdPercent must be in [1, 100], got " + thresholdPercent);
        }
    }
}

package io.taanielo.jmud.core.achievement;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.player.Player;

/**
 * Immutable definition of an achievement milestone loaded from a {@code data/achievements/*.json}
 * file.
 *
 * <p>An achievement unlocks once the player's progress for its {@link #condition()} reaches
 * {@link #threshold()} (e.g. the {@code total_kills} condition with a threshold of {@code 100}
 * unlocks on the player's hundredth kill).
 *
 * @param id          unique achievement id
 * @param name        human-readable display name (e.g. {@code "Centurion"})
 * @param description short flavour description of how the achievement is earned
 * @param condition   the player statistic this achievement tracks
 * @param threshold   the value {@link #condition()} must reach to unlock; must be at least 1
 * @param titleReward optional title granted to the player when this achievement unlocks (same shape
 *                    as a quest {@code title_reward}), or {@code null} when the milestone grants no
 *                    title
 */
public record Achievement(
    AchievementId id,
    String name,
    String description,
    AchievementCondition condition,
    int threshold,
    @Nullable String titleReward
) {
    public Achievement {
        Objects.requireNonNull(id, "Achievement id is required");
        Objects.requireNonNull(name, "Achievement name is required");
        Objects.requireNonNull(description, "Achievement description is required");
        Objects.requireNonNull(condition, "Achievement condition is required");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Achievement name must not be blank");
        }
        if (threshold < 1) {
            throw new IllegalArgumentException("Achievement threshold must be at least 1, got " + threshold);
        }
        if (titleReward != null && titleReward.isBlank()) {
            throw new IllegalArgumentException("Achievement title reward must not be blank when present");
        }
    }

    /**
     * Convenience constructor for an achievement that grants no title reward.
     *
     * @param id          unique achievement id
     * @param name        human-readable display name
     * @param description short flavour description
     * @param condition   the player statistic this achievement tracks
     * @param threshold   the value {@link #condition()} must reach to unlock; must be at least 1
     */
    public Achievement(
        AchievementId id,
        String name,
        String description,
        AchievementCondition condition,
        int threshold
    ) {
        this(id, name, description, condition, threshold, null);
    }

    /**
     * Returns the player's current progress toward this achievement, capped at {@link #threshold()}
     * so a completed milestone reads e.g. {@code 100/100} rather than {@code 137/100}.
     *
     * @param player the player to inspect; must not be null
     * @return the current progress value, in {@code [0, threshold]}
     */
    public long progress(Player player) {
        Objects.requireNonNull(player, "player is required");
        return Math.min(threshold, Math.max(0L, condition.currentValue(player)));
    }

    /**
     * Returns {@code true} when the given player currently satisfies this achievement's condition.
     *
     * @param player the player to evaluate; must not be null
     */
    public boolean isSatisfiedBy(Player player) {
        Objects.requireNonNull(player, "player is required");
        return condition.currentValue(player) >= threshold;
    }
}

package io.taanielo.jmud.core.achievement;

import java.util.Locale;

import io.taanielo.jmud.core.player.Player;

/**
 * The kind of milestone an {@link Achievement} tracks, together with how to read a player's current
 * progress toward it.
 *
 * <p>Each condition compares a monotonically non-decreasing player statistic against the
 * achievement's threshold: the achievement unlocks the moment the statistic reaches the threshold.
 */
public enum AchievementCondition {

    /** Cumulative number of mobs the player has killed. */
    TOTAL_KILLS("kills") {
        @Override
        public long currentValue(Player player) {
            return player.getTotalKills();
        }
    },

    /** The player's character level. */
    LEVEL("level") {
        @Override
        public long currentValue(Player player) {
            return player.getLevel();
        }
    };

    private final String progressUnit;

    AchievementCondition(String progressUnit) {
        this.progressUnit = progressUnit;
    }

    /**
     * Returns the player's current progress value for this condition (e.g. their total kills).
     *
     * @param player the player to inspect; must not be null
     * @return the current, non-negative progress value
     */
    public abstract long currentValue(Player player);

    /**
     * Returns the short unit label used when rendering progress (e.g. {@code "kills"} yields
     * {@code "5/100 kills"}).
     */
    public String progressUnit() {
        return progressUnit;
    }

    /**
     * Parses a condition from its lower-case JSON token (e.g. {@code "total_kills"}).
     *
     * @param token the JSON condition token; must not be null
     * @return the matching condition
     * @throws IllegalArgumentException when the token matches no condition
     */
    public static AchievementCondition fromToken(String token) {
        return valueOf(token.trim().toUpperCase(Locale.ROOT));
    }
}

package io.taanielo.jmud.core.player;

/**
 * Domain service that awards XP to a player and triggers level-ups when the
 * XP threshold is reached.
 *
 * <p>Formula:
 * <ul>
 *   <li>XP required to advance from level {@code N} to {@code N+1}: {@code N * 100}</li>
 *   <li>Each level-up grants {@code +10} permanent max HP and {@code +5} permanent max mana.</li>
 * </ul>
 *
 * <p>The service is stateless and thread-safe; callers are responsible for
 * persisting the returned {@link LevelUpResult}.
 */
public class LevelUpService {

    /** XP needed to advance from level {@code level} to the next level. */
    public static long xpForNextLevel(int level) {
        return (long) level * 100;
    }

    /** Max-HP gain applied per level-up. */
    public static final int HP_GAIN_PER_LEVEL = 10;

    /** Max-mana gain applied per level-up. */
    public static final int MANA_GAIN_PER_LEVEL = 5;

    /**
     * Awards the given amount of XP to the player and resolves any resulting
     * level-ups.
     *
     * @param player    the player receiving XP; must not be null
     * @param xpAmount  the amount of XP to award; must be non-negative
     * @return a {@link LevelUpResult} containing the updated player and a flag
     *         indicating whether at least one level-up occurred
     */
    public LevelUpResult awardXp(Player player, long xpAmount) {
        if (xpAmount < 0) {
            throw new IllegalArgumentException("xpAmount must be non-negative");
        }
        long newXp = player.getExperience() + xpAmount;
        int newLevel = player.getLevel();
        PlayerVitals newVitals = player.getVitals();
        boolean leveledUp = false;

        // Resolve multiple level-ups in case the XP gain is large
        while (newXp >= xpForNextLevel(newLevel)) {
            newXp -= xpForNextLevel(newLevel);
            newLevel++;
            int newMaxHp = newVitals.maxHp() + HP_GAIN_PER_LEVEL;
            int newMaxMana = newVitals.maxMana() + MANA_GAIN_PER_LEVEL;
            // Restore player to full on level-up (classic MUD behaviour)
            newVitals = new PlayerVitals(
                newMaxHp, newMaxHp, newMaxHp,
                newMaxMana, newMaxMana,
                newVitals.maxMove(), newVitals.maxMove()
            );
            leveledUp = true;
        }

        Player updated = player
            .withIdentity(player.identity()
                .withLevel(newLevel)
                .withExperience(newXp))
            .withVitals(newVitals);

        return new LevelUpResult(updated, leveledUp);
    }

    /**
     * Result of an XP award operation.
     *
     * @param player    the updated player (with new XP, level, and vitals if applicable)
     * @param leveledUp {@code true} if at least one level-up occurred
     */
    public record LevelUpResult(Player player, boolean leveledUp) {}
}

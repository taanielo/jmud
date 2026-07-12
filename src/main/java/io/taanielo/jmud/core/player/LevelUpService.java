package io.taanielo.jmud.core.player;

import java.util.Objects;

import io.taanielo.jmud.core.character.ClassLevelGainsResolver;
import io.taanielo.jmud.core.character.LevelGains;

/**
 * Domain service that awards XP to a player and triggers level-ups when the
 * XP threshold is reached.
 *
 * <p>Formula:
 * <ul>
 *   <li>XP required to advance from level {@code N} to {@code N+1}: {@code N * 100}</li>
 *   <li>Each level-up grants permanent max HP, mana and move determined by the player's class
 *       (see {@link ClassLevelGainsResolver}). Classes without configured gains, and unclassed
 *       characters, fall back to {@link LevelGains#DEFAULT} ({@code +10} HP, {@code +5} mana,
 *       {@code +3} move).</li>
 * </ul>
 *
 * <p>The service is stateless and thread-safe; callers are responsible for
 * persisting the returned {@link LevelUpResult}. Class gains are resolved from an in-memory
 * snapshot captured at construction, so a level-up never touches the class repository or disk on
 * the tick thread (AGENTS.md §5).
 */
public class LevelUpService {

    /** XP needed to advance from level {@code level} to the next level. */
    public static long xpForNextLevel(int level) {
        return (long) level * 100;
    }

    /** Default max-HP gain applied per level-up when a class has no configured gains. */
    public static final int HP_GAIN_PER_LEVEL = LevelGains.DEFAULT.hp();

    /** Default max-mana gain applied per level-up when a class has no configured gains. */
    public static final int MANA_GAIN_PER_LEVEL = LevelGains.DEFAULT.mana();

    /** Default max-move gain applied per level-up when a class has no configured gains. */
    public static final int MOVE_GAIN_PER_LEVEL = LevelGains.DEFAULT.move();

    /** Practice points awarded per level-up. */
    public static final int PRACTICE_POINTS_PER_LEVEL = 1;

    private final ClassLevelGainsResolver levelGainsResolver;

    /**
     * Creates a service that applies the legacy default gains to every class.
     *
     * <p>Retained for tests and legacy callers; production code should inject a class-aware
     * resolver via {@link #LevelUpService(ClassLevelGainsResolver)}.
     */
    public LevelUpService() {
        this(ClassLevelGainsResolver.defaultGains());
    }

    /**
     * Creates a service that resolves per-level gains from the given class-gains lookup.
     *
     * @param levelGainsResolver resolves a player's class to its per-level {@link LevelGains};
     *                           must not be null
     */
    public LevelUpService(ClassLevelGainsResolver levelGainsResolver) {
        this.levelGainsResolver = Objects.requireNonNull(levelGainsResolver, "levelGainsResolver must not be null");
    }

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
        int newPracticePoints = player.getPracticePoints();
        boolean leveledUp = false;

        LevelGains gains = levelGainsResolver.gainsFor(player.getClassId());

        // Resolve multiple level-ups in case the XP gain is large
        while (newXp >= xpForNextLevel(newLevel)) {
            newXp -= xpForNextLevel(newLevel);
            newLevel++;
            int newMaxHp = newVitals.maxHp() + gains.hp();
            int newMaxMana = newVitals.maxMana() + gains.mana();
            int newMaxMove = newVitals.maxMove() + gains.move();
            // Restore player to full on level-up (classic MUD behaviour)
            newVitals = new PlayerVitals(
                newMaxHp, newMaxHp, newMaxHp,
                newMaxMana, newMaxMana,
                newMaxMove, newMaxMove
            );
            newPracticePoints += PRACTICE_POINTS_PER_LEVEL;
            leveledUp = true;
        }

        Player updated = player
            .withIdentity(player.identity()
                .withLevel(newLevel)
                .withExperience(newXp))
            .withVitals(newVitals)
            .withPracticePoints(newPracticePoints);

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

package io.taanielo.jmud.core.achievement;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.player.Player;

/**
 * Domain service that evaluates milestone {@link Achievement} conditions against a player and unlocks
 * any newly satisfied achievements.
 *
 * <p>Achievement definitions are snapshotted into memory at construction so every evaluation is a
 * pure in-memory pass — no disk I/O ever runs on the tick thread (AGENTS.md §5). Condition checks are
 * deterministic functions of the player's persisted stats; the only wall-clock value is the unlock
 * timestamp, sourced from an injected {@link Clock} so the service stays unit-testable.
 *
 * <p>The service is otherwise stateless and safe to share across connections.
 */
public final class AchievementService {

    private final List<Achievement> definitions;
    private final Clock clock;

    /**
     * Creates a service over all achievement definitions from the given repository, using the system
     * UTC clock for unlock timestamps.
     *
     * @param repository the achievement repository to load definitions from
     * @throws AchievementRepositoryException when the definitions cannot be loaded
     */
    public AchievementService(AchievementRepository repository) throws AchievementRepositoryException {
        this(repository, Clock.systemUTC());
    }

    /**
     * Creates a service over all achievement definitions from the given repository, using the given
     * clock for unlock timestamps.
     *
     * @param repository the achievement repository to load definitions from
     * @param clock      the clock used to timestamp unlocks; must not be null
     * @throws AchievementRepositoryException when the definitions cannot be loaded
     */
    public AchievementService(AchievementRepository repository, Clock clock)
        throws AchievementRepositoryException {
        Objects.requireNonNull(repository, "Achievement repository is required");
        this.clock = Objects.requireNonNull(clock, "Clock is required");
        List<Achievement> loaded = new ArrayList<>(repository.findAll());
        // Display order: group by condition, then ascending threshold, then id for stability.
        loaded.sort(Comparator
            .comparing((Achievement a) -> a.condition().name())
            .thenComparingInt(Achievement::threshold)
            .thenComparing(a -> a.id().getValue()));
        this.definitions = List.copyOf(loaded);
    }

    /**
     * Returns all achievement definitions in display order.
     */
    public List<Achievement> definitions() {
        return definitions;
    }

    /**
     * Evaluates every achievement against the given player and unlocks any that are newly satisfied,
     * stamping each with the current clock instant.
     *
     * <p>Tick-thread only (AGENTS.md §5): called from milestone events (mob kills, level-ups) so the
     * returned, updated player can be persisted by the caller.
     *
     * @param player the player to evaluate; must not be null
     * @return an {@link UnlockResult} with the updated player and the achievements just unlocked
     *         (both unchanged/empty when nothing new was earned)
     */
    public UnlockResult checkAndUnlock(Player player) {
        Objects.requireNonNull(player, "player is required");
        Instant now = clock.instant();
        PlayerAchievements current = player.achievements();
        PlayerAchievements updated = current;
        List<Achievement> newlyUnlocked = new ArrayList<>();
        for (Achievement achievement : definitions) {
            if (!updated.has(achievement.id()) && achievement.isSatisfiedBy(player)) {
                updated = updated.unlock(achievement.id(), now);
                newlyUnlocked.add(achievement);
            }
        }
        if (newlyUnlocked.isEmpty()) {
            return new UnlockResult(player, List.of());
        }
        return new UnlockResult(player.withAchievements(updated), List.copyOf(newlyUnlocked));
    }

    /**
     * Returns the unlock/progress status of every achievement for the given player, in display
     * order, for rendering the {@code ACHIEVEMENTS} command.
     *
     * @param player the player to inspect; must not be null
     * @return one {@link AchievementStatus} per known achievement
     */
    public List<AchievementStatus> statuses(Player player) {
        Objects.requireNonNull(player, "player is required");
        PlayerAchievements achievements = player.achievements();
        List<AchievementStatus> statuses = new ArrayList<>(definitions.size());
        for (Achievement achievement : definitions) {
            boolean unlocked = achievements.has(achievement.id());
            Instant unlockedAt = achievements.unlockedAt(achievement.id()).orElse(null);
            long progress = achievement.progress(player);
            statuses.add(new AchievementStatus(achievement, unlocked, unlockedAt, progress));
        }
        return List.copyOf(statuses);
    }

    /**
     * Result of an {@link #checkAndUnlock(Player)} pass.
     *
     * @param player        the player with any newly unlocked achievements applied
     * @param newlyUnlocked the achievements unlocked by this pass (empty when none)
     */
    public record UnlockResult(Player player, List<Achievement> newlyUnlocked) {}

    /**
     * The unlock/progress status of a single achievement for a player.
     *
     * @param achievement the achievement definition
     * @param unlocked    whether the player has unlocked it
     * @param unlockedAt  when it was unlocked, or {@code null} when still locked
     * @param progress    current progress toward the threshold, in {@code [0, threshold]}
     */
    public record AchievementStatus(
        Achievement achievement,
        boolean unlocked,
        @Nullable Instant unlockedAt,
        long progress
    ) {}
}

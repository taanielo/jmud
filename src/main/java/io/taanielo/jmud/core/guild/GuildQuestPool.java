package io.taanielo.jmud.core.guild;

import java.util.List;
import java.util.Objects;

/**
 * The ordered catalogue of {@link GuildQuestObjective}s a guild's active guild quest is drawn from.
 *
 * <p>Unlike a personal daily-quest pool, guild-quest assignment is level-banded: a guild is only ever
 * assigned an objective whose {@link GuildQuestObjective#minGuildLevel()} is at or below the guild's
 * current {@link GuildLevel} rank (see {@link #objectivesUpToLevel(int)}), so a fledgling guild gets an
 * achievable target while an established guild unlocks the tougher, higher-reward objectives. Within an
 * eligible band the active objective advances deterministically from a rotation counter, mirroring the
 * daily-quest rotation (AGENTS.md §5).
 *
 * @param objectives the ordered, non-empty list of guild-quest objectives, from low to high band
 */
public record GuildQuestPool(List<GuildQuestObjective> objectives) {

    public GuildQuestPool {
        Objects.requireNonNull(objectives, "objectives is required");
        if (objectives.isEmpty()) {
            throw new IllegalArgumentException("A guild quest pool must contain at least one objective");
        }
        objectives = List.copyOf(objectives);
        boolean hasLevelOne = objectives.stream().anyMatch(o -> o.minGuildLevel() == 1);
        if (!hasLevelOne) {
            throw new IllegalArgumentException(
                "A guild quest pool must contain at least one level-1 objective so every guild is eligible");
        }
    }

    /**
     * Returns the objectives eligible for a guild of the given level rank, i.e. those whose
     * {@link GuildQuestObjective#minGuildLevel()} is at or below {@code guildLevelRank}, preserving pool
     * order. A guild of any level always has at least the level-1 objectives available.
     *
     * @param guildLevelRank the guild's {@link GuildLevel#rank()} (1-5)
     * @return the non-empty list of eligible objectives, in pool order
     */
    public List<GuildQuestObjective> objectivesUpToLevel(int guildLevelRank) {
        List<GuildQuestObjective> eligible = objectives.stream()
            .filter(o -> o.minGuildLevel() <= guildLevelRank)
            .toList();
        if (eligible.isEmpty()) {
            // Defensive: the invariant guarantees a level-1 objective, so this only fires for a
            // nonsensical rank < 1. Fall back to the whole pool rather than assign nothing.
            return objectives;
        }
        return eligible;
    }
}

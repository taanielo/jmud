package io.taanielo.jmud.core.guild;

import java.util.Objects;

/**
 * A single guild-quest template in the rotating {@link GuildQuestPool}: a cooperative "slay N of a
 * mob type" objective assignable to a whole guild.
 *
 * <p>Objectives carry a {@link #minGuildLevel()} band so that low-level guilds are only ever assigned
 * achievable targets while higher-level guilds unlock the tougher, higher-reward objectives (see
 * {@link GuildQuestService#assignObjectiveFor}). The reward is paid straight into the guild treasury on
 * completion, so it also counts toward the guild's lifetime deposited gold and next {@link GuildLevel}.
 *
 * @param questId       unique id of this objective across the whole pool
 * @param name          display name shown to guild members (e.g. "Cull the Dire Wolves")
 * @param targetMobId   the mob template id a guild member must kill to progress
 * @param targetName    plural display noun used in progress lines (e.g. "dire wolves")
 * @param requiredKills the number of matching kills needed to complete the objective (positive)
 * @param goldReward    the gold paid into the guild treasury on completion (non-negative)
 * @param minGuildLevel the lowest {@link GuildLevel} rank (1-5) a guild must be to be assigned this
 *                      objective; level-1 objectives are always eligible for every guild
 */
public record GuildQuestObjective(
    String questId,
    String name,
    String targetMobId,
    String targetName,
    int requiredKills,
    int goldReward,
    int minGuildLevel
) {

    public GuildQuestObjective {
        Objects.requireNonNull(questId, "questId is required");
        Objects.requireNonNull(name, "name is required");
        Objects.requireNonNull(targetMobId, "targetMobId is required");
        Objects.requireNonNull(targetName, "targetName is required");
        if (questId.isBlank()) {
            throw new IllegalArgumentException("questId must not be blank");
        }
        if (targetMobId.isBlank()) {
            throw new IllegalArgumentException("targetMobId must not be blank");
        }
        if (requiredKills <= 0) {
            throw new IllegalArgumentException("requiredKills must be positive");
        }
        if (goldReward < 0) {
            throw new IllegalArgumentException("goldReward must not be negative");
        }
        if (minGuildLevel < 1 || minGuildLevel > 5) {
            throw new IllegalArgumentException("minGuildLevel must be between 1 and 5");
        }
    }
}

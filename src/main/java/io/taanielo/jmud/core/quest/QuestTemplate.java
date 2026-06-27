package io.taanielo.jmud.core.quest;

import java.util.Objects;

/**
 * Immutable definition of a quest contract loaded from data files.
 *
 * @param id            unique quest identifier
 * @param name          display name shown to players
 * @param description   short flavour description of the quest goal
 * @param targetMobId   mob template id that must be killed to progress
 * @param requiredKills number of kills needed to complete the quest
 * @param goldReward    bonus gold awarded on completion
 * @param xpReward      bonus XP awarded on completion
 */
public record QuestTemplate(
    QuestId id,
    String name,
    String description,
    String targetMobId,
    int requiredKills,
    int goldReward,
    int xpReward
) {
    public QuestTemplate {
        Objects.requireNonNull(id, "Quest id is required");
        Objects.requireNonNull(name, "Quest name is required");
        Objects.requireNonNull(description, "Quest description is required");
        Objects.requireNonNull(targetMobId, "Quest targetMobId is required");
        if (requiredKills <= 0) {
            throw new IllegalArgumentException("requiredKills must be positive");
        }
        if (goldReward < 0) {
            throw new IllegalArgumentException("goldReward must be non-negative");
        }
        if (xpReward < 0) {
            throw new IllegalArgumentException("xpReward must be non-negative");
        }
    }
}

package io.taanielo.jmud.core.quest;

import java.util.Objects;

/**
 * Immutable definition of a quest contract loaded from data files.
 *
 * <p>Two quest types are supported:
 * <ul>
 *   <li><b>Kill quest</b> — {@code targetMobId} and {@code requiredKills} are set;
 *       {@code dropItemId} and {@code requiredDropCount} are {@code null}/0.</li>
 *   <li><b>Delivery quest</b> — {@code dropItemId} and {@code requiredDropCount} are set;
 *       {@code targetMobId} and {@code requiredKills} are {@code null}/0.</li>
 * </ul>
 *
 * @param id                unique quest identifier
 * @param name              display name shown to players
 * @param description       short flavour description of the quest goal
 * @param targetMobId       mob template id that must be killed to progress (kill quests only)
 * @param requiredKills     number of kills needed to complete the quest (kill quests only)
 * @param goldReward        bonus gold awarded on completion
 * @param xpReward          bonus XP awarded on completion
 * @param dropItemId        item id that must be collected and turned in (delivery quests only)
 * @param requiredDropCount number of drop items needed to complete the quest (delivery quests only)
 */
public record QuestTemplate(
    QuestId id,
    String name,
    String description,
    String targetMobId,
    int requiredKills,
    int goldReward,
    int xpReward,
    String dropItemId,
    int requiredDropCount
) {
    public QuestTemplate {
        Objects.requireNonNull(id, "Quest id is required");
        Objects.requireNonNull(name, "Quest name is required");
        Objects.requireNonNull(description, "Quest description is required");
        boolean isKillQuest = targetMobId != null && requiredKills > 0;
        boolean isDeliveryQuest = dropItemId != null && requiredDropCount > 0;
        if (!isKillQuest && !isDeliveryQuest) {
            throw new IllegalArgumentException(
                "Quest must define either kill targets (targetMobId + requiredKills) "
                + "or drop targets (dropItemId + requiredDropCount)");
        }
        if (goldReward < 0) {
            throw new IllegalArgumentException("goldReward must be non-negative");
        }
        if (xpReward < 0) {
            throw new IllegalArgumentException("xpReward must be non-negative");
        }
    }

    /**
     * Convenience constructor for kill-count quests (no delivery fields).
     *
     * @param id            unique quest identifier
     * @param name          display name shown to players
     * @param description   short flavour description of the quest goal
     * @param targetMobId   mob template id that must be killed to progress
     * @param requiredKills number of kills needed to complete the quest
     * @param goldReward    bonus gold awarded on completion
     * @param xpReward      bonus XP awarded on completion
     */
    public QuestTemplate(
        QuestId id,
        String name,
        String description,
        String targetMobId,
        int requiredKills,
        int goldReward,
        int xpReward
    ) {
        this(id, name, description, targetMobId, requiredKills, goldReward, xpReward, null, 0);
    }

    /**
     * Returns {@code true} when this is a delivery quest where the player must
     * collect a specific drop item and turn it in to the Guild Clerk.
     */
    public boolean isDeliveryQuest() {
        return dropItemId != null && requiredDropCount > 0;
    }
}

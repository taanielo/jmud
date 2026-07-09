package io.taanielo.jmud.core.quest;

import java.util.Objects;

/**
 * Immutable definition of a quest contract loaded from data files.
 *
 * <p>Three quest types are supported (see {@link QuestType}); exactly one set of
 * type-specific fields must be populated:
 * <ul>
 *   <li><b>Kill quest</b> — {@code targetMobId} and {@code requiredKills} are set.</li>
 *   <li><b>Item delivery quest</b> — {@code dropItemId} and {@code requiredDropCount} are set.</li>
 *   <li><b>NPC delivery quest</b> — {@code giverNpcId}, {@code receiverNpcId},
 *       {@code receiverRoomId} and {@code packageItemId} are set: a giver NPC hands the
 *       player a package to carry to a receiver NPC in another zone.</li>
 * </ul>
 *
 * @param id                unique quest identifier
 * @param name              display name shown to players
 * @param description       short flavour description of the quest goal
 * @param targetMobId       mob template id that must be killed to progress (kill quests only)
 * @param requiredKills     number of kills needed to complete the quest (kill quests only)
 * @param goldReward        bonus gold awarded on completion
 * @param xpReward          bonus XP awarded on completion
 * @param dropItemId        item id that must be collected and turned in (item delivery quests only)
 * @param requiredDropCount number of drop items needed to complete the quest (item delivery quests only)
 * @param titleReward       title granted to the player on completion, or {@code null} when none is granted
 * @param giverNpcId        mob template id of the NPC who hands over the package (NPC delivery quests only)
 * @param receiverNpcId     mob template id of the NPC the package must be delivered to (NPC delivery quests only)
 * @param receiverRoomId    room id where the receiver NPC stands (NPC delivery quests only)
 * @param packageItemId     item id of the package handed to the player (NPC delivery quests only)
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
    int requiredDropCount,
    String titleReward,
    String giverNpcId,
    String receiverNpcId,
    String receiverRoomId,
    String packageItemId
) {
    public QuestTemplate {
        Objects.requireNonNull(id, "Quest id is required");
        Objects.requireNonNull(name, "Quest name is required");
        Objects.requireNonNull(description, "Quest description is required");
        boolean isKillQuest = targetMobId != null && requiredKills > 0;
        boolean isItemDeliveryQuest = dropItemId != null && requiredDropCount > 0;
        boolean isNpcDeliveryQuest = giverNpcId != null && receiverNpcId != null
            && receiverRoomId != null && packageItemId != null;
        int typesDefined = (isKillQuest ? 1 : 0) + (isItemDeliveryQuest ? 1 : 0) + (isNpcDeliveryQuest ? 1 : 0);
        if (typesDefined != 1) {
            throw new IllegalArgumentException(
                "Quest must define exactly one of: kill targets (targetMobId + requiredKills), "
                + "item delivery (dropItemId + requiredDropCount), "
                + "or NPC delivery (giverNpcId + receiverNpcId + receiverRoomId + packageItemId)");
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
        this(id, name, description, targetMobId, requiredKills, goldReward, xpReward,
            null, 0, null, null, null, null, null);
    }

    /**
     * Convenience constructor for kill or item-delivery quests (no NPC delivery fields).
     *
     * @param id                unique quest identifier
     * @param name              display name shown to players
     * @param description       short flavour description of the quest goal
     * @param targetMobId       mob template id that must be killed (kill quests only)
     * @param requiredKills     number of kills needed (kill quests only)
     * @param goldReward        bonus gold awarded on completion
     * @param xpReward          bonus XP awarded on completion
     * @param dropItemId        item id that must be collected (item delivery quests only)
     * @param requiredDropCount number of drop items needed (item delivery quests only)
     * @param titleReward       title granted on completion, or {@code null}
     */
    public QuestTemplate(
        QuestId id,
        String name,
        String description,
        String targetMobId,
        int requiredKills,
        int goldReward,
        int xpReward,
        String dropItemId,
        int requiredDropCount,
        String titleReward
    ) {
        this(id, name, description, targetMobId, requiredKills, goldReward, xpReward,
            dropItemId, requiredDropCount, titleReward, null, null, null, null);
    }

    /**
     * Returns the type of this quest, derived from which set of type-specific fields is populated.
     */
    public QuestType type() {
        if (isNpcDeliveryQuest()) {
            return QuestType.DELIVERY_NPC;
        }
        if (isDeliveryQuest()) {
            return QuestType.DELIVERY_ITEM;
        }
        return QuestType.KILL;
    }

    /**
     * Returns {@code true} when this is an item-delivery quest where the player must
     * collect a specific drop item and turn it in to the Guild Clerk.
     */
    public boolean isDeliveryQuest() {
        return dropItemId != null && requiredDropCount > 0;
    }

    /**
     * Returns {@code true} when this is an NPC delivery quest where the player carries a
     * package from a giver NPC to a receiver NPC standing in another zone.
     */
    public boolean isNpcDeliveryQuest() {
        return giverNpcId != null && receiverNpcId != null
            && receiverRoomId != null && packageItemId != null;
    }
}

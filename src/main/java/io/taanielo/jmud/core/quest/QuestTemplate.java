package io.taanielo.jmud.core.quest;

import java.util.List;
import java.util.Objects;

/**
 * Immutable definition of a quest contract loaded from data files.
 *
 * <p>Four quest types are supported (see {@link QuestType}); exactly one set of
 * type-specific fields must be populated:
 * <ul>
 *   <li><b>Kill quest</b> — {@code targetMobId} and {@code requiredKills} are set.</li>
 *   <li><b>Item delivery quest</b> — {@code dropItemId} and {@code requiredDropCount} are set.</li>
 *   <li><b>NPC delivery quest</b> — {@code giverNpcId}, {@code receiverNpcId},
 *       {@code receiverRoomId} and {@code packageItemId} are set: a giver NPC hands the
 *       player a package to carry to a receiver NPC in another zone.</li>
 *   <li><b>Exploration quest</b> — {@code requiredRoomIds} lists the rooms the player must
 *       visit; the quest auto-completes once every room has been entered.</li>
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
 * @param requiredRoomIds   room ids that must all be visited to complete the quest (exploration quests only);
 *                          never {@code null} — an empty list means this is not an exploration quest
 * @param dailyPoolId       identifier of the daily rotation pool this quest belongs to, or {@code null}
 *                          when the quest is a normal (non-daily) contract. Daily quests are otherwise
 *                          ordinary kill quests grouped into a pool that rotates one active quest per game day.
 * @param itemReward        item id granted directly into the player's inventory on completion, or
 *                          {@code null} when the quest awards no item. Applicable to any quest type.
 * @param itemRewardQuantity number of copies of {@code itemReward} to grant; must be positive when
 *                          {@code itemReward} is set and exactly zero when it is {@code null}
 * @param reputationRewardFactionId id of the faction whose standing changes on completion, or
 *                          {@code null} when the quest grants no reputation reward. Applicable to any
 *                          quest type.
 * @param reputationRewardDelta signed change applied to the player's standing with
 *                          {@code reputationRewardFactionId} on completion; must be non-zero when a
 *                          faction id is set and exactly zero when it is {@code null}
 * @param repeatable        {@code true} when the quest may be accepted and completed repeatedly (the
 *                          default, preserving legacy behaviour); {@code false} for one-time-only
 *                          contracts that a player may complete just once
 * @param prerequisiteQuestId id of a quest the player must have completed before this contract can be
 *                          accepted, or {@code null} when the quest has no prerequisite
 * @param recommendedLevel  the character level this contract is balanced for, shown as a difficulty
 *                          hint in {@code QUEST LIST} and used to sort contracts easiest-first; a
 *                          value of {@code 0} means the quest carries no level recommendation
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
    String packageItemId,
    List<String> requiredRoomIds,
    String dailyPoolId,
    String itemReward,
    int itemRewardQuantity,
    String reputationRewardFactionId,
    int reputationRewardDelta,
    boolean repeatable,
    String prerequisiteQuestId,
    int recommendedLevel
) {
    public QuestTemplate {
        Objects.requireNonNull(id, "Quest id is required");
        Objects.requireNonNull(name, "Quest name is required");
        Objects.requireNonNull(description, "Quest description is required");
        requiredRoomIds = requiredRoomIds == null ? List.of() : List.copyOf(requiredRoomIds);
        boolean isKillQuest = targetMobId != null && requiredKills > 0;
        boolean isItemDeliveryQuest = dropItemId != null && requiredDropCount > 0;
        boolean isNpcDeliveryQuest = giverNpcId != null && receiverNpcId != null
            && receiverRoomId != null && packageItemId != null;
        boolean isExplorationQuest = !requiredRoomIds.isEmpty();
        int typesDefined = (isKillQuest ? 1 : 0) + (isItemDeliveryQuest ? 1 : 0)
            + (isNpcDeliveryQuest ? 1 : 0) + (isExplorationQuest ? 1 : 0);
        if (typesDefined != 1) {
            throw new IllegalArgumentException(
                "Quest must define exactly one of: kill targets (targetMobId + requiredKills), "
                + "item delivery (dropItemId + requiredDropCount), "
                + "NPC delivery (giverNpcId + receiverNpcId + receiverRoomId + packageItemId), "
                + "or exploration (requiredRoomIds)");
        }
        if (goldReward < 0) {
            throw new IllegalArgumentException("goldReward must be non-negative");
        }
        if (xpReward < 0) {
            throw new IllegalArgumentException("xpReward must be non-negative");
        }
        if (itemReward != null && itemRewardQuantity <= 0) {
            throw new IllegalArgumentException(
                "itemRewardQuantity must be positive when itemReward is set");
        }
        if (itemReward == null && itemRewardQuantity != 0) {
            throw new IllegalArgumentException(
                "itemRewardQuantity must be zero when itemReward is not set");
        }
        if (reputationRewardFactionId != null && reputationRewardDelta == 0) {
            throw new IllegalArgumentException(
                "reputationRewardDelta must be non-zero when reputationRewardFactionId is set");
        }
        if (reputationRewardFactionId == null && reputationRewardDelta != 0) {
            throw new IllegalArgumentException(
                "reputationRewardDelta must be zero when reputationRewardFactionId is not set");
        }
        if (recommendedLevel < 0) {
            throw new IllegalArgumentException("recommendedLevel must be non-negative");
        }
    }

    /**
     * Convenience constructor for kill-count quests (no delivery or exploration fields).
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
            null, 0, null, null, null, null, null, List.of(), null, null, 0, null, 0, true, null, 0);
    }

    /**
     * Convenience constructor for kill or item-delivery quests (no NPC delivery or exploration fields).
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
            dropItemId, requiredDropCount, titleReward, null, null, null, null, List.of(), null, null, 0, null, 0, true, null, 0);
    }

    /**
     * Convenience constructor for kill, item-delivery, or NPC-delivery quests (no exploration fields).
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
     * @param giverNpcId        mob template id of the giver NPC (NPC delivery quests only)
     * @param receiverNpcId     mob template id of the receiver NPC (NPC delivery quests only)
     * @param receiverRoomId    room id where the receiver NPC stands (NPC delivery quests only)
     * @param packageItemId     item id of the package (NPC delivery quests only)
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
        String titleReward,
        String giverNpcId,
        String receiverNpcId,
        String receiverRoomId,
        String packageItemId
    ) {
        this(id, name, description, targetMobId, requiredKills, goldReward, xpReward,
            dropItemId, requiredDropCount, titleReward, giverNpcId, receiverNpcId,
            receiverRoomId, packageItemId, List.of(), null, null, 0, null, 0, true, null, 0);
    }

    /**
     * Convenience constructor for exploration quests (no kill or delivery fields).
     *
     * @param id              unique quest identifier
     * @param name            display name shown to players
     * @param description     short flavour description of the quest goal
     * @param goldReward      bonus gold awarded on completion
     * @param xpReward        bonus XP awarded on completion
     * @param titleReward     title granted on completion, or {@code null}
     * @param requiredRoomIds room ids that must all be visited to complete the quest
     */
    public QuestTemplate(
        QuestId id,
        String name,
        String description,
        int goldReward,
        int xpReward,
        String titleReward,
        List<String> requiredRoomIds
    ) {
        this(id, name, description, null, 0, goldReward, xpReward,
            null, 0, titleReward, null, null, null, null, requiredRoomIds, null, null, 0, null, 0, true, null, 0);
    }

    /**
     * Convenience constructor for a daily kill quest belonging to a rotation pool.
     *
     * @param id            unique quest identifier
     * @param name          display name shown to players
     * @param description   short flavour description of the quest goal
     * @param targetMobId   mob template id that must be killed to progress
     * @param requiredKills number of kills needed to complete the quest
     * @param goldReward    bonus gold awarded on completion
     * @param xpReward      bonus XP awarded on completion
     * @param dailyPoolId   identifier of the daily rotation pool this quest belongs to
     */
    public QuestTemplate(
        QuestId id,
        String name,
        String description,
        String targetMobId,
        int requiredKills,
        int goldReward,
        int xpReward,
        String dailyPoolId
    ) {
        this(id, name, description, targetMobId, requiredKills, goldReward, xpReward,
            null, 0, null, null, null, null, null, List.of(), dailyPoolId, null, 0, null, 0, true, null, 0);
    }

    /**
     * Returns a copy of this quest with the given item reward attached.
     *
     * @param itemReward         item id to grant on completion; {@code null} clears any item reward
     * @param itemRewardQuantity number of copies to grant; must be positive when {@code itemReward}
     *                           is set and zero otherwise
     * @return a new {@link QuestTemplate} carrying the item reward
     */
    public QuestTemplate withItemReward(String itemReward, int itemRewardQuantity) {
        return new QuestTemplate(id, name, description, targetMobId, requiredKills, goldReward, xpReward,
            dropItemId, requiredDropCount, titleReward, giverNpcId, receiverNpcId, receiverRoomId,
            packageItemId, requiredRoomIds, dailyPoolId, itemReward, itemRewardQuantity,
            reputationRewardFactionId, reputationRewardDelta, repeatable, prerequisiteQuestId, recommendedLevel);
    }

    /**
     * Returns a copy of this quest with the given reputation reward attached.
     *
     * @param reputationRewardFactionId id of the faction whose standing changes on completion;
     *                                  {@code null} clears any reputation reward
     * @param reputationRewardDelta     signed change to apply; must be non-zero when a faction id is
     *                                  set and zero otherwise
     * @return a new {@link QuestTemplate} carrying the reputation reward
     */
    public QuestTemplate withReputationReward(String reputationRewardFactionId, int reputationRewardDelta) {
        return new QuestTemplate(id, name, description, targetMobId, requiredKills, goldReward, xpReward,
            dropItemId, requiredDropCount, titleReward, giverNpcId, receiverNpcId, receiverRoomId,
            packageItemId, requiredRoomIds, dailyPoolId, itemReward, itemRewardQuantity,
            reputationRewardFactionId, reputationRewardDelta, repeatable, prerequisiteQuestId, recommendedLevel);
    }

    /**
     * Returns {@code true} when this quest grants a guaranteed item reward on completion.
     */
    public boolean hasItemReward() {
        return itemReward != null;
    }

    /**
     * Returns {@code true} when this quest changes the player's standing with a faction on completion.
     */
    public boolean hasReputationReward() {
        return reputationRewardFactionId != null;
    }

    /**
     * Returns {@code true} when this quest may be accepted and completed repeatedly (the default);
     * {@code false} for one-time-only contracts a player may complete just once.
     */
    public boolean isRepeatable() {
        return repeatable;
    }

    /**
     * Returns {@code true} when this quest requires the player to have completed another quest first.
     */
    public boolean hasPrerequisite() {
        return prerequisiteQuestId != null;
    }

    /**
     * Returns {@code true} when this quest carries a recommended character level, i.e. a non-zero
     * {@link #recommendedLevel()} used as a difficulty hint and easiest-first sort key in
     * {@code QUEST LIST}.
     */
    public boolean hasRecommendedLevel() {
        return recommendedLevel > 0;
    }

    /**
     * Returns the type of this quest, derived from which set of type-specific fields is populated.
     */
    public QuestType type() {
        if (isExplorationQuest()) {
            return QuestType.EXPLORATION;
        }
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

    /**
     * Returns {@code true} when this is an exploration quest where the player must visit every
     * room listed in {@link #requiredRoomIds()}.
     */
    public boolean isExplorationQuest() {
        return !requiredRoomIds.isEmpty();
    }

    /**
     * Returns {@code true} when this quest belongs to a daily rotation pool, i.e. it is one of the
     * rotating repeatable contracts surfaced by the {@code DAILY_QUEST} command rather than a normal
     * Guild Clerk contract.
     */
    public boolean isDaily() {
        return dailyPoolId != null;
    }
}

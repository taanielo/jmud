package io.taanielo.jmud.core.quest.repository.json;

import java.util.List;

/**
 * JSON transfer object for a quest definition file ({@code quest.*.json}).
 *
 * <p>Fields are grouped by quest type:
 * <ul>
 *   <li>Kill quests (schema version 1) use {@code targetMobId} and {@code requiredKills}.</li>
 *   <li>Item delivery quests (schema version 2) use {@code dropItemId} and {@code requiredDropCount}.</li>
 *   <li>NPC delivery quests (schema version 3) use {@code giverNpcId}, {@code receiverNpcId},
 *       {@code receiverRoomId} and {@code packageItemId}.</li>
 *   <li>Exploration quests (schema version 4) use {@code requiredRoomIds}.</li>
 * </ul>
 *
 * <p>{@code type} is an optional human-readable discriminator ("kill", "delivery-item",
 * "delivery-npc", "exploration") kept for schema documentation; the effective type is derived from
 * which type-specific fields are populated. {@code titleReward} is optional for any quest type.
 *
 * <p>{@code itemReward}/{@code itemRewardQuantity} are optional and apply to any quest type: when
 * {@code itemReward} names an item id, that many copies are granted directly to the player's
 * inventory on completion. {@code itemRewardQuantity} defaults to {@code 1} when an item reward is
 * present but no quantity is specified.
 *
 * <p>{@code reputationRewardFactionId}/{@code reputationRewardDelta} are optional and apply to any
 * quest type: when {@code reputationRewardFactionId} names a faction id, the player's standing with
 * that faction changes by {@code reputationRewardDelta} (a signed, non-zero integer) on completion.
 *
 * <p>{@code repeatable} is optional and defaults to {@code true} when absent, preserving legacy
 * behaviour: existing quests may be accepted and completed repeatedly. When set to {@code false} the
 * quest becomes a one-time-only contract a player may complete just once. {@code prerequisiteQuestId}
 * is optional: when set, the quest can only be accepted after the player has completed the named
 * prerequisite quest.
 */
record QuestDto(
    int schemaVersion,
    String type,
    String id,
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
    String itemReward,
    int itemRewardQuantity,
    String reputationRewardFactionId,
    int reputationRewardDelta,
    Boolean repeatable,
    String prerequisiteQuestId
) {
}

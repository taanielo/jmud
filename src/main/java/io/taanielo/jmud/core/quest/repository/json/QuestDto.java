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
    List<String> requiredRoomIds
) {
}

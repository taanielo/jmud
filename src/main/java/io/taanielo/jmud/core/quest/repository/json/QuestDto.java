package io.taanielo.jmud.core.quest.repository.json;

/**
 * JSON transfer object for a quest definition file ({@code quest.*.json}).
 */
record QuestDto(
    int schemaVersion,
    String id,
    String name,
    String description,
    String targetMobId,
    int requiredKills,
    int goldReward,
    int xpReward
) {
}

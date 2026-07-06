package io.taanielo.jmud.core.quest.repository.json;

/**
 * JSON transfer object for a quest definition file ({@code quest.*.json}).
 *
 * <p>Fields {@code dropItemId} and {@code requiredDropCount} are optional and
 * used only for delivery quests (schema version 2). Kill quests (schema version 1)
 * use {@code targetMobId} and {@code requiredKills} instead.
 *
 * <p>{@code titleReward} is optional for either quest type; when present, the
 * player is granted this title on quest completion.
 */
record QuestDto(
    int schemaVersion,
    String id,
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
}

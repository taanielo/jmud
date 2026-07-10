package io.taanielo.jmud.core.quest.repository.json;

import java.util.List;

/**
 * JSON transfer object for a daily quest pool file ({@code data/quests/daily/*.json}, schema
 * version 5).
 *
 * <p>A pool groups several kill-quest variants that share a single daily rotation slot; one variant
 * is active per game day (see {@code DailyQuestService}). {@code type} is an optional human-readable
 * discriminator ("daily") kept for documentation; the loader ignores unknown properties.
 *
 * @param schemaVersion the schema version, expected to be 5
 * @param type          optional human-readable discriminator ("daily")
 * @param poolId        the pool identifier, accepted via {@code DAILY_QUEST ACCEPT <pool_id>}
 * @param name          display name of the pool
 * @param quests        the ordered list of quest variants that rotate through this pool
 */
record DailyQuestPoolDto(
    int schemaVersion,
    String type,
    String poolId,
    String name,
    List<DailyQuestVariantDto> quests
) {

    /**
     * JSON transfer object for a single daily quest variant within a pool.
     *
     * @param id           unique quest id
     * @param name         display name shown to players
     * @param description  short flavour description of the quest goal
     * @param targetMobId  mob template id that must be killed to progress
     * @param requiredKills number of kills needed to complete the quest
     * @param goldReward   bonus gold awarded on completion
     * @param xpReward     bonus XP awarded on completion
     * @param titleReward  optional title granted on completion
     * @param itemReward   optional item id granted directly to inventory on completion
     * @param itemRewardQuantity number of copies of {@code itemReward} to grant (defaults to 1)
     * @param reputationRewardFactionId optional faction id whose standing changes on completion
     * @param reputationRewardDelta signed, non-zero standing change applied when
     *                     {@code reputationRewardFactionId} is set
     */
    record DailyQuestVariantDto(
        String id,
        String name,
        String description,
        String targetMobId,
        int requiredKills,
        int goldReward,
        int xpReward,
        String titleReward,
        String itemReward,
        int itemRewardQuantity,
        String reputationRewardFactionId,
        int reputationRewardDelta
    ) {
    }
}

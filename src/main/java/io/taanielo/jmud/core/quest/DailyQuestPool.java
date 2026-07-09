package io.taanielo.jmud.core.quest;

import java.util.List;
import java.util.Objects;

/**
 * An ordered pool of daily quest variants that share a rotation slot.
 *
 * <p>Exactly one variant of a pool is "active" at any time; the active variant advances by one
 * position each game day (see {@link DailyQuestService}). Every variant in a pool is an ordinary
 * kill quest whose {@link QuestTemplate#dailyPoolId()} equals this pool's {@link #poolId()}.
 *
 * @param poolId  unique identifier of the pool, accepted via {@code DAILY_QUEST ACCEPT <pool_id>}
 * @param name    display name of the pool shown to players
 * @param quests  the ordered, non-empty list of quest variants that rotate through this pool
 */
public record DailyQuestPool(String poolId, String name, List<QuestTemplate> quests) {

    public DailyQuestPool {
        Objects.requireNonNull(poolId, "poolId is required");
        Objects.requireNonNull(name, "name is required");
        Objects.requireNonNull(quests, "quests is required");
        if (poolId.isBlank()) {
            throw new IllegalArgumentException("poolId must not be blank");
        }
        if (quests.isEmpty()) {
            throw new IllegalArgumentException("A daily quest pool must contain at least one quest variant");
        }
        quests = List.copyOf(quests);
        for (QuestTemplate quest : quests) {
            if (!poolId.equals(quest.dailyPoolId())) {
                throw new IllegalArgumentException(
                    "Quest " + quest.id().getValue() + " does not belong to daily pool " + poolId);
            }
        }
    }

    /**
     * Returns the quest variant occupying the given rotation index, wrapping modulo the pool size so
     * any (possibly ever-increasing) rotation counter maps to a valid variant.
     *
     * @param rotationIndex the rotation counter; may be any non-negative value
     * @return the active quest variant for that rotation position
     */
    public QuestTemplate variantAt(long rotationIndex) {
        if (rotationIndex < 0) {
            throw new IllegalArgumentException("rotationIndex must be non-negative");
        }
        int index = (int) (rotationIndex % quests.size());
        return quests.get(index);
    }
}

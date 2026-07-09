package io.taanielo.jmud.core.quest;

import java.util.List;

/**
 * Repository for loading {@link DailyQuestPool} definitions.
 *
 * <p>Implementations must be thread-safe after construction.
 */
public interface DailyQuestPoolRepository {

    /**
     * Returns all available daily quest pools.
     *
     * @return immutable list of daily quest pools; never null
     * @throws QuestRepositoryException if daily quest data cannot be loaded
     */
    List<DailyQuestPool> findAll() throws QuestRepositoryException;
}

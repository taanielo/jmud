package io.taanielo.jmud.core.quest;

import java.util.List;
import java.util.Optional;

/**
 * Repository for loading {@link QuestTemplate} definitions.
 *
 * <p>Implementations must be thread-safe after construction.
 */
public interface QuestRepository {

    /**
     * Returns all available quest templates.
     *
     * @return immutable list of quest templates; never null
     * @throws QuestRepositoryException if quest data cannot be loaded
     */
    List<QuestTemplate> findAll() throws QuestRepositoryException;

    /**
     * Returns the quest template with the given id, if present.
     *
     * @param id the quest id to look up; must not be null
     * @return the matching template, or empty
     * @throws QuestRepositoryException if quest data cannot be loaded
     */
    Optional<QuestTemplate> findById(QuestId id) throws QuestRepositoryException;
}

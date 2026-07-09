package io.taanielo.jmud.core.achievement;

import java.util.List;
import java.util.Optional;

/**
 * Domain port for loading {@link Achievement} definitions. Implementations live in the
 * infrastructure layer (e.g. a JSON-backed repository) and are constructed only by the composition
 * root.
 */
public interface AchievementRepository {

    /**
     * Returns all known achievement definitions.
     *
     * @return the loaded achievements (never null, may be empty)
     * @throws AchievementRepositoryException when the definitions cannot be loaded
     */
    List<Achievement> findAll() throws AchievementRepositoryException;

    /**
     * Returns the achievement with the given id, if defined.
     *
     * @param id the achievement id to look up
     * @return the matching achievement, or empty when none is defined
     * @throws AchievementRepositoryException when the definitions cannot be loaded
     */
    Optional<Achievement> findById(AchievementId id) throws AchievementRepositoryException;
}

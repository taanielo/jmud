package io.taanielo.jmud.core.faction;

import java.util.List;
import java.util.Optional;

/**
 * Domain port for loading {@link Faction} definitions. Implementations live in the infrastructure
 * layer (e.g. a JSON-backed repository) and are constructed only by the composition root.
 */
public interface FactionRepository {

    /**
     * Returns all known faction definitions.
     *
     * @return the loaded factions (never null, may be empty)
     * @throws FactionRepositoryException when the definitions cannot be loaded
     */
    List<Faction> findAll() throws FactionRepositoryException;

    /**
     * Returns the faction with the given id, if defined.
     *
     * @param factionId the faction id to look up
     * @return the matching faction, or empty when none is defined
     * @throws FactionRepositoryException when the definitions cannot be loaded
     */
    Optional<Faction> findById(FactionId factionId) throws FactionRepositoryException;
}

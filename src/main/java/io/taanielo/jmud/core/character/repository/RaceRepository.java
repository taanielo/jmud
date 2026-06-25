package io.taanielo.jmud.core.character.repository;

import java.util.List;
import java.util.Optional;

import io.taanielo.jmud.core.character.Race;
import io.taanielo.jmud.core.character.RaceId;

/** Repository for {@link Race} data. */
public interface RaceRepository {
    /** Finds a race by its id, returning empty if not found. */
    Optional<Race> findById(RaceId id) throws RaceRepositoryException;

    /** Returns all available races in an unspecified order. */
    List<Race> findAll() throws RaceRepositoryException;
}

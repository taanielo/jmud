package io.taanielo.jmud.core.character.repository;

import java.util.Optional;

import io.taanielo.jmud.core.character.Race;
import io.taanielo.jmud.core.character.RaceId;

public interface RaceRepository {
    Optional<Race> findById(RaceId id) throws RaceRepositoryException;
}

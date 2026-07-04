package io.taanielo.jmud.core.combat.repository;

import java.util.Optional;

import io.taanielo.jmud.core.combat.AttackDefinition;
import io.taanielo.jmud.core.combat.AttackId;
import io.taanielo.jmud.core.world.repository.RepositoryException;

public interface AttackRepository {
    Optional<AttackDefinition> findById(AttackId id) throws RepositoryException;
}

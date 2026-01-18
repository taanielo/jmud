package io.taanielo.jmud.core.ability.repository;

import java.util.List;
import java.util.Optional;

import io.taanielo.jmud.core.ability.Ability;
import io.taanielo.jmud.core.ability.AbilityId;

public interface AbilityRepository {
    Optional<Ability> findById(AbilityId id) throws AbilityRepositoryException;

    List<Ability> findAll() throws AbilityRepositoryException;
}

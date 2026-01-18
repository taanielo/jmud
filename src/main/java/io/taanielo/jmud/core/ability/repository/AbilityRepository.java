package io.taanielo.jmud.core.ability.repository;

import java.util.List;
import java.util.Optional;

import io.taanielo.jmud.core.ability.Ability;

public interface AbilityRepository {
    Optional<Ability> findById(String id) throws AbilityRepositoryException;

    List<Ability> findAll() throws AbilityRepositoryException;
}

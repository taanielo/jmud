package io.taanielo.jmud.core.character.repository;

import java.util.List;
import java.util.Optional;

import io.taanielo.jmud.core.character.ClassDefinition;
import io.taanielo.jmud.core.character.ClassId;

/** Repository for {@link ClassDefinition} data. */
public interface ClassRepository {
    /** Finds a class by its id, returning empty if not found. */
    Optional<ClassDefinition> findById(ClassId id) throws ClassRepositoryException;

    /** Returns all available class definitions in an unspecified order. */
    List<ClassDefinition> findAll() throws ClassRepositoryException;
}

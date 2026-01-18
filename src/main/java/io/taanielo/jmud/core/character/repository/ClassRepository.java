package io.taanielo.jmud.core.character.repository;

import java.util.Optional;

import io.taanielo.jmud.core.character.ClassDefinition;
import io.taanielo.jmud.core.character.ClassId;

public interface ClassRepository {
    Optional<ClassDefinition> findById(ClassId id) throws ClassRepositoryException;
}

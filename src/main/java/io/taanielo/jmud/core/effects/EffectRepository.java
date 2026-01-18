package io.taanielo.jmud.core.effects;

import java.util.Optional;

public interface EffectRepository {
    Optional<EffectDefinition> findById(EffectId id) throws EffectRepositoryException;
}

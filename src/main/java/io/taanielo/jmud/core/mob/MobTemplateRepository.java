package io.taanielo.jmud.core.mob;

import java.util.List;

import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Provides mob template definitions loaded from persistent storage.
 */
public interface MobTemplateRepository {

    List<MobTemplate> findAll() throws RepositoryException;
}

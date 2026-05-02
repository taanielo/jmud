package io.taanielo.jmud.core.mob;

import java.util.List;

/**
 * Provides mob template definitions loaded from persistent storage.
 */
public interface MobTemplateRepository {

    List<MobTemplate> findAll() throws MobRepositoryException;
}

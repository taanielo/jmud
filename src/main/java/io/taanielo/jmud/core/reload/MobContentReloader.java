package io.taanielo.jmud.core.reload;

import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Reads and validates all mob-template content off the tick thread for a hot reload (issue #349).
 *
 * <p>Implemented by the live mob registry. Committing the returned {@link PreparedReload} replaces
 * the registry's cached templates, so subsequent spawns (wizard {@code SPAWN}, tamed-pet respawn)
 * use the updated definitions; mobs already living in the world are left untouched.
 */
@FunctionalInterface
public interface MobContentReloader {

    /**
     * Reads and validates every mob-template JSON file into an in-memory snapshot without mutating
     * live state.
     *
     * @return a prepared reload ready to be committed on the tick thread
     * @throws RepositoryException if any mob file fails to parse or validate; live templates are
     *     left unchanged
     */
    PreparedReload prepareMobs() throws RepositoryException;
}

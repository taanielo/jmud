package io.taanielo.jmud.core.combat.flavor.repository;

import io.taanielo.jmud.core.combat.flavor.CombatFlavor;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Loads the combat-flavor word tables (damage verbs and target conditions) from storage.
 */
public interface CombatFlavorRepository {

    /**
     * Loads the combat-flavor tables.
     *
     * @return the loaded {@link CombatFlavor}
     * @throws RepositoryException if the data cannot be read or fails validation
     */
    CombatFlavor load() throws RepositoryException;
}

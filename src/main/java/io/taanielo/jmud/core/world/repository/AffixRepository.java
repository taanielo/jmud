package io.taanielo.jmud.core.world.repository;

import java.util.List;
import java.util.Optional;

import io.taanielo.jmud.core.world.AffixId;
import io.taanielo.jmud.core.world.ItemAffix;

/**
 * Read-only access to the data-driven stat affix definitions (see {@code data/item-affixes.json}).
 * Implementations load affixes from persistent storage; domain services depend on this interface
 * rather than any concrete loader (AGENTS.md §3.3).
 */
public interface AffixRepository {

    /**
     * Finds an affix definition by id.
     *
     * @param id the affix id to look up
     * @return the matching affix, or empty when none is defined
     * @throws RepositoryException if the affix data cannot be read
     */
    Optional<ItemAffix> findById(AffixId id) throws RepositoryException;

    /**
     * Returns every defined affix.
     *
     * @return all affix definitions, never null
     * @throws RepositoryException if the affix data cannot be read
     */
    List<ItemAffix> findAll() throws RepositoryException;
}

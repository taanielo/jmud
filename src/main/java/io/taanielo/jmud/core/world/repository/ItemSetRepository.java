package io.taanielo.jmud.core.world.repository;

import java.util.List;
import java.util.Optional;

import io.taanielo.jmud.core.world.ItemSet;
import io.taanielo.jmud.core.world.ItemSetId;

/**
 * Read-only access to the data-driven item-set definitions (see {@code data/item-sets/}).
 * Implementations load sets from persistent storage; domain services depend on this interface rather
 * than any concrete loader (AGENTS.md §3.3).
 */
public interface ItemSetRepository {

    /**
     * Finds an item set by id.
     *
     * @param id the item set id to look up
     * @return the matching set, or empty when none is defined
     * @throws RepositoryException if the item-set data cannot be read
     */
    Optional<ItemSet> findById(ItemSetId id) throws RepositoryException;

    /**
     * Returns every defined item set.
     *
     * @return all item-set definitions, never null
     * @throws RepositoryException if the item-set data cannot be read
     */
    List<ItemSet> findAll() throws RepositoryException;
}

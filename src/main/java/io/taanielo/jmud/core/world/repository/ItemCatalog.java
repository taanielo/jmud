package io.taanielo.jmud.core.world.repository;

import java.util.List;

import io.taanielo.jmud.core.world.Item;

/**
 * Read-only bulk access to every item definition in the world.
 *
 * <p>Separate from {@link ItemRepository} (which only offers single-item lookup and save) so that
 * whole-world content-completeness tooling (e.g. the item-obtainability validator, issue #530) can
 * enumerate every item without forcing every lightweight {@code ItemRepository} test double to
 * implement a bulk query. The concrete data-backed repository implements both interfaces.
 */
public interface ItemCatalog {

    /**
     * Returns every item definition known to the repository.
     *
     * @return all items (never {@code null})
     * @throws RepositoryException when the item data cannot be read
     */
    List<Item> findAll() throws RepositoryException;
}

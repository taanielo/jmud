package io.taanielo.jmud.core.reload;

import java.util.Optional;

import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Resolves an {@link Item} by id during a room reload.
 *
 * <p>Rooms reference the items lying in them by id, so reloading a room requires resolving each id
 * to a full {@link Item}. During a hot reload this lookup is backed by the newly prepared item
 * snapshot first, falling back to the currently live item repository, so a room may reference an
 * item that was added in the same reload (issue #349).
 */
@FunctionalInterface
public interface ItemLookup {

    /**
     * Finds the item with the given id.
     *
     * @param id the item id to resolve
     * @return the matching item, or empty when no item with that id exists
     * @throws RepositoryException if resolving the item fails (e.g. a corrupt backing file)
     */
    Optional<Item> find(ItemId id) throws RepositoryException;
}

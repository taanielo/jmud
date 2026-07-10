package io.taanielo.jmud.core.reload;

import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Reads and validates all item content off the tick thread for a hot reload (issue #349).
 *
 * <p>Implemented by the JSON item repository. {@link #prepareItems()} performs the file I/O and
 * validation; the returned {@link PreparedItemReload} is committed later on the tick thread.
 */
@FunctionalInterface
public interface ItemContentReloader {

    /**
     * Reads and validates every item JSON file into an in-memory snapshot without mutating live
     * state.
     *
     * @return a prepared reload ready to be committed on the tick thread
     * @throws RepositoryException if any item file fails to parse or validate; live items are
     *     left unchanged
     */
    PreparedItemReload prepareItems() throws RepositoryException;
}

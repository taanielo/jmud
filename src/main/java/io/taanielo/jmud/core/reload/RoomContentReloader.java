package io.taanielo.jmud.core.reload;

import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Reads and validates all room content off the tick thread for a hot reload (issue #349).
 *
 * <p>Implemented by the JSON room repository. Rooms reference items by id, so {@link #prepareRooms}
 * takes an {@link ItemLookup} used to resolve those references against the freshly prepared item
 * snapshot (falling back to the live item repository).
 */
@FunctionalInterface
public interface RoomContentReloader {

    /**
     * Reads and validates every room JSON file into an in-memory snapshot without mutating live
     * state.
     *
     * @param itemLookup resolves item references found in room files
     * @return a prepared reload ready to be committed on the tick thread
     * @throws RepositoryException if any room file fails to parse, validate, or references a
     *     missing item; live rooms are left unchanged
     */
    PreparedReload prepareRooms(ItemLookup itemLookup) throws RepositoryException;
}

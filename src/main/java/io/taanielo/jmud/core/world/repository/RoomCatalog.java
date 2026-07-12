package io.taanielo.jmud.core.world.repository;

import java.util.List;

import io.taanielo.jmud.core.world.Room;

/**
 * Read-only bulk access to every room in the world.
 *
 * <p>Separate from {@link RoomRepository} so that whole-world consistency tooling (e.g. the area
 * validator, issue #529) can enumerate rooms without forcing every lightweight {@code
 * RoomRepository} test double to implement a bulk query. The concrete data-backed repositories
 * implement both interfaces.
 */
public interface RoomCatalog {

    /**
     * Returns every room known to the repository.
     *
     * @return all rooms (never {@code null})
     * @throws RepositoryException when the room data cannot be read
     */
    List<Room> findAll() throws RepositoryException;
}

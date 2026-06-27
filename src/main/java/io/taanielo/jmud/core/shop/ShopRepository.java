package io.taanielo.jmud.core.shop;

import java.util.List;
import java.util.Optional;

import io.taanielo.jmud.core.world.RoomId;

/**
 * Data-access contract for {@link Shop} definitions.
 */
public interface ShopRepository {

    /**
     * Returns all shop definitions.
     *
     * @throws ShopRepositoryException when data cannot be read
     */
    List<Shop> findAll() throws ShopRepositoryException;

    /**
     * Returns the shop whose {@code roomId} matches the given room, or empty
     * when there is no shop in that room.
     *
     * @throws ShopRepositoryException when data cannot be read
     */
    Optional<Shop> findByRoomId(RoomId roomId) throws ShopRepositoryException;
}

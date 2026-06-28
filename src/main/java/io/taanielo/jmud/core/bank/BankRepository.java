package io.taanielo.jmud.core.bank;

import java.util.List;
import java.util.Optional;

import io.taanielo.jmud.core.world.RoomId;

/**
 * Data-access contract for {@link Bank} definitions.
 */
public interface BankRepository {

    /**
     * Returns all bank definitions.
     *
     * @throws BankRepositoryException when data cannot be read
     */
    List<Bank> findAll() throws BankRepositoryException;

    /**
     * Returns the bank whose {@code roomId} matches the given room, or empty
     * when there is no bank in that room.
     *
     * @throws BankRepositoryException when data cannot be read
     */
    Optional<Bank> findByRoomId(RoomId roomId) throws BankRepositoryException;
}

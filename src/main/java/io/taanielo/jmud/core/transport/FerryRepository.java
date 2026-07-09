package io.taanielo.jmud.core.transport;

import java.util.List;

/**
 * Domain port that supplies the world's configured {@link Ferry} definitions.
 *
 * <p>Implementations live in the infrastructure layer (e.g. a JSON-backed repository); the
 * {@link BoatEngine} depends only on this interface (AGENTS.md §3.2).
 */
public interface FerryRepository {

    /**
     * Returns every configured ferry, in a stable order.
     *
     * @return the ferries (may be empty, never {@code null})
     */
    List<Ferry> findAll();
}

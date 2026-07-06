package io.taanielo.jmud.core.player;

import java.util.List;
import java.util.Optional;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Persists and retrieves {@link Player} state.
 */
public interface PlayerRepository {

    /**
     * Persists the given player's current state.
     *
     * @param player the player to save
     * @throws RepositoryException if the player could not be persisted; callers must not
     *                              assume the save succeeded and should surface the failure
     *                              (log, audit, and/or warn the player) rather than swallow it
     */
    void savePlayer(Player player) throws RepositoryException;

    Optional<Player> loadPlayer(Username username);

    /**
     * Loads every persisted player, including those not currently online.
     *
     * <p>This scans persisted storage and is a blocking I/O operation; callers must
     * never invoke it from code reachable from the tick thread (see AGENTS.md §5).
     * It is intended for read-only, reader-thread commands such as a global kill
     * ranking listing.
     *
     * <p>The default implementation returns an empty list so existing test doubles
     * do not need to be updated.
     *
     * @return an immutable snapshot of all persisted players
     */
    default List<Player> findAll() {
        return List.of();
    }
}

package io.taanielo.jmud.core.player;

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
}

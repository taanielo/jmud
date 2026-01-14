package io.taanielo.jmud.core.player;

import java.util.Optional;

import io.taanielo.jmud.core.authentication.Username;

public interface PlayerRepository {
    void savePlayer(Player player);
    Optional<Player> loadPlayer(Username username);
}

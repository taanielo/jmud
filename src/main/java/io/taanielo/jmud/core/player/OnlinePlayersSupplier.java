package io.taanielo.jmud.core.player;

import java.util.List;

import io.taanielo.jmud.core.authentication.Username;

/**
 * Domain-level port that supplies the usernames of all players currently online and in the world.
 *
 * <p>Keeps tick-driven domain services (such as {@link ArenaEventTicker}) decoupled from the
 * transport layer: the composition root ({@code GameContext}) provides the concrete adapter that
 * enumerates connected clients, while the domain depends only on this interface (AGENTS.md §3.2).
 */
@FunctionalInterface
public interface OnlinePlayersSupplier {

    /**
     * Returns a snapshot of the usernames of every player currently connected and in the world.
     *
     * @return an immutable snapshot list of online usernames; never {@code null}, possibly empty
     */
    List<Username> onlineUsernames();
}

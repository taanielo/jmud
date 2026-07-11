package io.taanielo.jmud.core.player;

import java.util.Optional;

import io.taanielo.jmud.core.authentication.Username;

/**
 * Domain-level port that resolves a currently-connected player by username.
 *
 * <p>Unlike {@link io.taanielo.jmud.core.ability.AbilityTargetResolver}, which only finds players
 * co-located in the actor's room, this port returns a player's live snapshot regardless of their
 * location — including dead players who have been removed from the room map while awaiting respawn.
 * It keeps tick-driven domain services (such as the Cleric resurrection spell in
 * {@code GameActionService}) decoupled from the transport layer: the composition root supplies the
 * concrete adapter that walks the connected clients, while the domain depends only on this
 * interface (AGENTS.md §3.2).
 */
@FunctionalInterface
public interface OnlinePlayerLookup {

    /** A lookup that never finds anyone; the safe default before the transport adapter is wired. */
    OnlinePlayerLookup NONE = username -> Optional.empty();

    /**
     * Returns the live snapshot of the connected player with the given username, if any.
     *
     * @param username the player to look up
     * @return the player's current state, or empty when no such player is connected
     */
    Optional<Player> find(Username username);
}

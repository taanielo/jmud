package io.taanielo.jmud.core.server;

import java.util.Optional;

import io.taanielo.jmud.core.messaging.Message;
import io.taanielo.jmud.core.player.Player;

public interface Client extends Runnable {

    void sendMessage(Message message);

    void close();

    /**
     * Returns the player currently authenticated on this client, if any.
     *
     * <p>Used by administrative flows (e.g. orderly shutdown) that need to
     * enumerate online players without depending on transport internals.
     * Defaults to {@link Optional#empty()} so existing/simple implementations
     * are unaffected.
     */
    default Optional<Player> currentPlayer() {
        return Optional.empty();
    }

    /**
     * Returns whether this connection currently has a player fully in the game world and
     * eligible to receive game broadcasts (room, GOSSIP, and similar channels).
     *
     * <p>A connection that is authenticated but still mid character-creation (race/class
     * selection) has {@link #currentPlayer()} populated but must not receive broadcasts
     * until creation completes and it enters the world. Defaults to mirroring
     * {@link #currentPlayer()} presence so implementations without a creation flow are
     * unaffected.
     */
    default boolean isInWorld() {
        return currentPlayer().isPresent();
    }
}

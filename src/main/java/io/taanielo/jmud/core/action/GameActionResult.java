package io.taanielo.jmud.core.action;

import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.player.Player;

/**
 * Result of a game action executed by {@link GameActionService}.
 *
 * <p>Captures updated player state and messages to deliver without performing
 * any I/O, allowing the adapter layer to handle delivery and auditing.
 */
public record GameActionResult(Player updatedSource, Player updatedTarget, List<GameMessage> messages) {

    public GameActionResult {
        Objects.requireNonNull(messages, "Messages list is required");
    }

    /** Creates an error result containing a single message to the acting player. */
    public static GameActionResult error(String message) {
        return new GameActionResult(null, null, List.of(GameMessage.toSource(message)));
    }

    /** Creates a result with an updated source player and a single message. */
    public static GameActionResult simple(Player updatedSource, String message) {
        return new GameActionResult(updatedSource, null, List.of(GameMessage.toSource(message)));
    }
}

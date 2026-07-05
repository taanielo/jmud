package io.taanielo.jmud.core.action;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.taanielo.jmud.core.player.Player;

/**
 * Result of a game action executed by {@link GameActionService}.
 *
 * <p>Captures updated player state and messages to deliver without performing
 * any I/O, allowing the adapter layer to handle delivery and auditing.
 *
 * <p>Optional metadata may be included for audit or diagnostic purposes. For
 * example, a successful attack includes a {@code "rngSeed"} entry so the combat
 * audit entry can record enough information to replay the encounter exactly.
 */
public record GameActionResult(
    Player updatedSource,
    Player updatedTarget,
    List<GameMessage> messages,
    Map<String, Object> metadata
) {

    public GameActionResult {
        Objects.requireNonNull(messages, "Messages list is required");
        metadata = (metadata == null) ? Map.of() : Map.copyOf(metadata);
    }

    /**
     * Convenience constructor that sets metadata to an empty map.
     * All existing call sites use this form; new combat-specific results may
     * supply metadata explicitly.
     */
    public GameActionResult(Player updatedSource, Player updatedTarget, List<GameMessage> messages) {
        this(updatedSource, updatedTarget, messages, Map.of());
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

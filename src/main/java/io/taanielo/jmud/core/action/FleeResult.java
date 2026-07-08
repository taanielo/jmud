package io.taanielo.jmud.core.action;

import java.util.Objects;

import io.taanielo.jmud.core.world.Direction;

/**
 * Outcome of a {@link GameActionService#flee(io.taanielo.jmud.core.player.Player,
 * io.taanielo.jmud.core.world.Room) flee} attempt.
 *
 * <p>Captures the game-rule decision only: whether the player successfully fled, the
 * randomly-chosen exit to move through when they did, and the message to show the player.
 * The actual movement (and its room-description output) is left to the adapter, so this
 * result carries no player state and performs no I/O.
 *
 * @param fled      {@code true} when the player disengaged and a flee direction was chosen
 * @param direction the exit chosen to flee through, or {@code null} when {@code fled} is false
 * @param message   the message to deliver to the fleeing player
 */
public record FleeResult(boolean fled, Direction direction, String message) {

    public FleeResult {
        Objects.requireNonNull(message, "Message is required");
    }

    /** Creates a failed flee result carrying only the reason message (no direction). */
    public static FleeResult failure(String message) {
        return new FleeResult(false, null, message);
    }

    /** Creates a successful flee result with the chosen exit direction and player message. */
    public static FleeResult success(Direction direction, String message) {
        return new FleeResult(true, Objects.requireNonNull(direction, "Direction is required"), message);
    }
}

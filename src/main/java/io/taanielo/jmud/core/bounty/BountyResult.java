package io.taanielo.jmud.core.bounty;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.player.Player;

/**
 * Outcome of a {@code BOUNTY POST} or {@code BOUNTY CANCEL} operation.
 *
 * <p>On success {@link #updatedActor()} is the invoking player with their gold balance adjusted
 * (debited on a post, refunded on a cancel) and the caller replaces the session player with it. On
 * failure {@link #updatedActor()} is {@code null} and no state changed; {@link #message()} always
 * carries the player-facing line to display.
 *
 * @param success       whether the operation succeeded
 * @param message       the player-facing result message (never {@code null})
 * @param updatedActor  the mutated invoking player on success, otherwise {@code null}
 */
public record BountyResult(boolean success, String message, @Nullable Player updatedActor) {

    public BountyResult {
        Objects.requireNonNull(message, "message is required");
    }

    /**
     * Creates a successful result carrying the updated player.
     *
     * @param message      the player-facing success message
     * @param updatedActor the invoking player with gold adjusted
     * @return a success result
     */
    public static BountyResult success(String message, Player updatedActor) {
        Objects.requireNonNull(updatedActor, "updatedActor is required");
        return new BountyResult(true, message, updatedActor);
    }

    /**
     * Creates a failure result with no state change.
     *
     * @param message the player-facing failure message
     * @return a failure result
     */
    public static BountyResult failure(String message) {
        return new BountyResult(false, message, null);
    }
}

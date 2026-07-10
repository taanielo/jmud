package io.taanielo.jmud.core.gathering;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.player.Player;

/**
 * Outcome of a {@link ResourceGatheringService} harvest attempt.
 *
 * <p>On success {@link #updatedPlayer()} holds the player with the harvested raw material added to
 * their inventory. On failure it is {@code null} and the player is unchanged.
 *
 * @param success       whether a resource was harvested
 * @param message       the player-facing message describing the outcome
 * @param updatedPlayer the player after the harvest on success, or {@code null} on failure
 */
public record GatherOutcome(boolean success, String message, @Nullable Player updatedPlayer) {

    /** Constructs a successful outcome carrying the updated player state. */
    public static GatherOutcome success(String message, Player updatedPlayer) {
        return new GatherOutcome(true, message, Objects.requireNonNull(updatedPlayer, "Player is required"));
    }

    /** Constructs a failure outcome with no player change. */
    public static GatherOutcome failure(String message) {
        return new GatherOutcome(false, message, null);
    }
}

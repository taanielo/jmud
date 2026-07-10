package io.taanielo.jmud.core.craft;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.player.Player;

/**
 * Outcome of a {@link CraftingService} craft attempt.
 *
 * <p>On success {@link #updatedPlayer()} holds the mutated player (materials and gold consumed, the
 * crafted item added). On failure it is {@code null} and nothing was consumed.
 *
 * @param success       whether the craft happened
 * @param message       the player-facing message describing the outcome
 * @param updatedPlayer the player after the craft on success, or {@code null} on failure
 */
public record CraftOutcome(boolean success, String message, @Nullable Player updatedPlayer) {

    /** Constructs a successful outcome carrying the updated player state. */
    public static CraftOutcome success(String message, Player updatedPlayer) {
        return new CraftOutcome(true, message, Objects.requireNonNull(updatedPlayer, "Player is required"));
    }

    /** Constructs a failure outcome with no player change. */
    public static CraftOutcome failure(String message) {
        return new CraftOutcome(false, message, null);
    }
}

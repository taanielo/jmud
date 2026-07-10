package io.taanielo.jmud.core.salvage;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.player.Player;

/**
 * Outcome of a {@link SalvageService} salvage attempt.
 *
 * <p>On success {@link #updatedPlayer()} holds the mutated player (the salvaged item removed and the
 * yielded materials added). On failure it is {@code null} and nothing was consumed.
 *
 * @param success       whether the salvage happened
 * @param message       the player-facing message describing the outcome
 * @param updatedPlayer the player after the salvage on success, or {@code null} on failure
 */
public record SalvageOutcome(boolean success, String message, @Nullable Player updatedPlayer) {

    /** Constructs a successful outcome carrying the updated player state. */
    public static SalvageOutcome success(String message, Player updatedPlayer) {
        return new SalvageOutcome(true, message, Objects.requireNonNull(updatedPlayer, "Player is required"));
    }

    /** Constructs a failure outcome with no player change. */
    public static SalvageOutcome failure(String message) {
        return new SalvageOutcome(false, message, null);
    }
}

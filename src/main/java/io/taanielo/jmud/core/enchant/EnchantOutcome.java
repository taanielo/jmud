package io.taanielo.jmud.core.enchant;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.player.Player;

/**
 * Result of an {@link EnchantingService#enchant} attempt: a player-facing message plus, on success,
 * the updated {@link Player} copy the caller applies on the tick thread (AGENTS.md §5). On failure
 * {@link #updatedPlayer()} is {@code null} and nothing is consumed.
 *
 * @param success       whether the enchantment succeeded
 * @param message       the player-facing message describing the result
 * @param updatedPlayer the updated player on success, or {@code null} on failure
 */
public record EnchantOutcome(boolean success, String message, @Nullable Player updatedPlayer) {

    public EnchantOutcome {
        Objects.requireNonNull(message, "Message is required");
    }

    /**
     * Creates a successful outcome carrying the updated player.
     *
     * @param message       the success message
     * @param updatedPlayer the updated player copy
     * @return a successful outcome
     */
    public static EnchantOutcome success(String message, Player updatedPlayer) {
        return new EnchantOutcome(true, message, Objects.requireNonNull(updatedPlayer, "Updated player is required"));
    }

    /**
     * Creates a failed outcome with no player change.
     *
     * @param message the failure message
     * @return a failed outcome
     */
    public static EnchantOutcome failure(String message) {
        return new EnchantOutcome(false, message, null);
    }
}

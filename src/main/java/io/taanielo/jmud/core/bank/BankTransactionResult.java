package io.taanielo.jmud.core.bank;

import io.taanielo.jmud.core.player.Player;

/**
 * Outcome of a {@link BankService} deposit or withdraw operation.
 *
 * <p>On success {@link #updatedPlayer()} holds the player with updated carried and banked
 * gold balances. On failure {@link #updatedPlayer()} is {@code null}.
 */
public record BankTransactionResult(boolean success, String message, Player updatedPlayer) {

    /** Constructs a successful result with an updated player state. */
    public static BankTransactionResult success(String message, Player updatedPlayer) {
        return new BankTransactionResult(true, message, updatedPlayer);
    }

    /** Constructs a failure result with no player change. */
    public static BankTransactionResult failure(String message) {
        return new BankTransactionResult(false, message, null);
    }
}

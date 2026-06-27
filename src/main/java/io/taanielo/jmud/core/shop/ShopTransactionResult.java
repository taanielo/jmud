package io.taanielo.jmud.core.shop;

import io.taanielo.jmud.core.player.Player;

/**
 * Outcome of a {@link ShopService} buy or sell operation.
 *
 * <p>On success {@link #updatedPlayer()} holds the mutated player (inventory
 * and/or gold changed). On failure {@link #updatedPlayer()} is {@code null}.
 */
public record ShopTransactionResult(boolean success, String message, Player updatedPlayer) {

    /** Constructs a successful result with an updated player state. */
    public static ShopTransactionResult success(String message, Player updatedPlayer) {
        return new ShopTransactionResult(true, message, updatedPlayer);
    }

    /** Constructs a failure result with no player change. */
    public static ShopTransactionResult failure(String message) {
        return new ShopTransactionResult(false, message, null);
    }
}

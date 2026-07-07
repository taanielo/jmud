package io.taanielo.jmud.core.player;

import java.util.List;

/**
 * Outcome of a {@link PlayerAliasService} operation.
 *
 * <p>On success {@link #updatedPlayer()} holds the player with the alias change applied.
 * On failure (validation error) {@link #updatedPlayer()} is {@code null} and only
 * {@link #message()} is set. {@link #lines()} is used for multi-line listings and is
 * empty for define/remove operations.
 */
public record AliasResult(boolean success, String message, Player updatedPlayer, List<String> lines) {

    /** Constructs a successful single-line result with an updated player state. */
    public static AliasResult success(String message, Player updatedPlayer) {
        return new AliasResult(true, message, updatedPlayer, List.of());
    }

    /** Constructs a successful multi-line listing result with no player change. */
    public static AliasResult listing(List<String> lines) {
        return new AliasResult(true, "", null, lines);
    }

    /** Constructs a failure result with no player change. */
    public static AliasResult failure(String message) {
        return new AliasResult(false, message, null, List.of());
    }
}

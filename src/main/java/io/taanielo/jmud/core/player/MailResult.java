package io.taanielo.jmud.core.player;

import java.util.List;

/**
 * Outcome of a {@link PlayerMailService} operation.
 *
 * <p>On success {@link #updatedPlayer()} holds the player whose mailbox changed (the sender
 * for {@code READ}/{@code DELETE}/list-viewing, or the recipient for a sent message). On
 * failure (validation error) {@link #updatedPlayer()} is {@code null} and only
 * {@link #message()} is set. {@link #lines()} is used for the multi-line {@code MAIL} listing
 * and is empty for send/read/delete operations.
 */
public record MailResult(boolean success, String message, Player updatedPlayer, List<String> lines) {

    /** Constructs a successful single-line result with an updated player state. */
    public static MailResult success(String message, Player updatedPlayer) {
        return new MailResult(true, message, updatedPlayer, List.of());
    }

    /**
     * Constructs a successful multi-line listing result, optionally with an updated player
     * (e.g. when viewing the list marks messages read).
     */
    public static MailResult listing(List<String> lines, Player updatedPlayer) {
        return new MailResult(true, "", updatedPlayer, lines);
    }

    /** Constructs a failure result with no player change. */
    public static MailResult failure(String message) {
        return new MailResult(false, message, null, List.of());
    }
}

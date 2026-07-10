package io.taanielo.jmud.core.player;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Outcome of a {@link PlayerMailService} operation.
 *
 * <p>On success {@link #updatedPlayer()} holds the player whose mailbox changed (the sender
 * for {@code READ}/{@code DELETE}/list-viewing, or the recipient for a sent message). On
 * failure (validation error) {@link #updatedPlayer()} is {@code null} and only
 * {@link #message()} is set. {@link #lines()} is used for the multi-line {@code MAIL} listing
 * and is empty for send/read/delete operations.
 *
 * <p>{@link #updatedSender()} is non-null only for {@code MAIL GOLD}: sending gold mutates two
 * distinct players — the recipient (mail appended, returned as {@link #updatedPlayer()}) and
 * the sender (gold deducted, returned here). All other operations touch a single player and
 * leave this {@code null}.
 */
public record MailResult(
    boolean success,
    String message,
    @Nullable Player updatedPlayer,
    List<String> lines,
    @Nullable Player updatedSender
) {

    /** Constructs a successful single-line result with an updated player state. */
    public static MailResult success(String message, Player updatedPlayer) {
        return new MailResult(true, message, updatedPlayer, List.of(), null);
    }

    /**
     * Constructs a successful {@code MAIL GOLD} result, carrying both the recipient (with the
     * new mail) and the sender (with gold deducted).
     */
    public static MailResult sentWithGold(String message, Player updatedRecipient, Player updatedSender) {
        return new MailResult(true, message, updatedRecipient, List.of(), updatedSender);
    }

    /**
     * Constructs a successful {@code MAIL ITEM} result, carrying both the recipient (with the
     * new mail and attached item) and the sender (with the item removed from their inventory).
     */
    public static MailResult sentWithItem(String message, Player updatedRecipient, Player updatedSender) {
        return new MailResult(true, message, updatedRecipient, List.of(), updatedSender);
    }

    /**
     * Constructs a successful multi-line listing result, optionally with an updated player
     * (e.g. when viewing the list marks messages read).
     */
    public static MailResult listing(List<String> lines, Player updatedPlayer) {
        return new MailResult(true, "", updatedPlayer, lines, null);
    }

    /** Constructs a failure result with no player change. */
    public static MailResult failure(String message) {
        return new MailResult(false, message, null, List.of(), null);
    }
}

package io.taanielo.jmud.core.server.socket;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.authentication.Username;

/**
 * Pure, network-free helpers for the AFK / away-from-keyboard feature (issue #464).
 *
 * <p>Encapsulates the two decisions with branching worth testing in isolation: how the {@code AFK}
 * command toggles away status (and which confirmation line it prints), and how a sender is told that
 * a TELL/WHISPER/REPLY recipient is away. Keeping this logic here — free of session mutation and
 * I/O — lets it be unit tested without a running {@code GameContext} (AGENTS.md §10).
 */
final class AfkStatus {

    private AfkStatus() {
    }

    /**
     * The outcome of applying an {@code AFK} toggle to a session.
     *
     * @param away         whether the session should now be marked away
     * @param message      the custom away reason to store, or {@code null} for the default
     * @param confirmation the line to show the caller
     */
    record ToggleResult(boolean away, @Nullable String message, String confirmation) {
    }

    /**
     * Computes the result of an {@code AFK} command against a session's current away state.
     *
     * <p>With no argument the command toggles: away → not away, or not away → away with the default
     * message. With an argument it always turns away status on with that custom reason (even when
     * already away, so the reason can be updated).
     *
     * @param currentlyAway whether the session is currently marked away
     * @param rawArgs       the raw command argument (a custom reason), possibly {@code null} or blank
     * @return the toggle outcome to apply and report
     */
    static ToggleResult toggle(boolean currentlyAway, @Nullable String rawArgs) {
        String message = rawArgs == null ? "" : rawArgs.trim();
        if (message.isEmpty() && currentlyAway) {
            return new ToggleResult(false, null, "You are no longer AFK.");
        }
        if (message.isEmpty()) {
            return new ToggleResult(true, null, "You are now AFK.");
        }
        return new ToggleResult(true, message, "You are now AFK: " + message);
    }

    /**
     * Formats the notice shown to a message sender when the recipient is away.
     *
     * @param recipient   the away recipient of the message
     * @param awayMessage the recipient's custom away reason, or {@code null}/blank for the default
     * @return {@code "<Name> is AFK: <reason>"} when a reason was set, otherwise {@code "<Name> is AFK."}
     */
    static String recipientNotice(Username recipient, @Nullable String awayMessage) {
        String name = recipient.getValue();
        return awayMessage == null || awayMessage.isBlank()
            ? name + " is AFK."
            : name + " is AFK: " + awayMessage;
    }
}

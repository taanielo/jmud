package io.taanielo.jmud.core.server.socket;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/**
 * Pure, network-free helpers for the LFG / looking-for-group feature (issue #510).
 *
 * <p>Encapsulates the decisions with branching worth testing in isolation: how the {@code LFG}
 * command toggles looking-for-group status (and which confirmation line it prints), how a
 * {@code LFG STATUS} query reports the current state, and how an LFG player is annotated in the
 * {@code WHO} roster. Keeping this logic here — free of session mutation and I/O — lets it be unit
 * tested without a running {@code GameContext} (AGENTS.md §10). It mirrors {@link AfkStatus}.
 */
final class LfgStatus {

    /** Argument that requests a state report instead of a toggle (case-insensitive). */
    static final String STATUS_ARG = "STATUS";

    private LfgStatus() {
    }

    /**
     * The outcome of applying an {@code LFG} toggle to a session.
     *
     * @param lfg          whether the session should now be flagged looking-for-group
     * @param message      the custom LFG note to store, or {@code null} for the default
     * @param confirmation the line to show the caller
     */
    record ToggleResult(boolean lfg, @Nullable String message, String confirmation) {
    }

    /**
     * Returns whether the raw argument is the {@code STATUS} query rather than a toggle/note.
     *
     * @param rawArgs the raw command argument, possibly {@code null} or blank
     * @return {@code true} when the argument requests a status report
     */
    static boolean isStatusQuery(@Nullable String rawArgs) {
        return rawArgs != null && rawArgs.trim().toUpperCase(Locale.ROOT).equals(STATUS_ARG);
    }

    /**
     * Computes the result of an {@code LFG} command against a session's current LFG state.
     *
     * <p>With no argument the command toggles: LFG on → off, or off → on with the default note. With
     * an argument it always turns LFG on with that custom note (even when already on, so the note can
     * be updated).
     *
     * @param currentlyLfg whether the session is currently flagged looking-for-group
     * @param rawArgs      the raw command argument (a custom note), possibly {@code null} or blank
     * @return the toggle outcome to apply and report
     */
    static ToggleResult toggle(boolean currentlyLfg, @Nullable String rawArgs) {
        String message = rawArgs == null ? "" : rawArgs.trim();
        if (message.isEmpty() && currentlyLfg) {
            return new ToggleResult(false, null, "You are no longer looking for a group.");
        }
        if (message.isEmpty()) {
            return new ToggleResult(true, null, "You are now looking for a group.");
        }
        return new ToggleResult(true, message, "You are now looking for a group: " + message);
    }

    /**
     * Formats the line shown by {@code LFG STATUS} describing the caller's current LFG state.
     *
     * @param currentlyLfg whether the session is currently flagged looking-for-group
     * @param lfgMessage   the session's custom LFG note, or {@code null}/blank for the default
     * @return the caller-facing status line
     */
    static String status(boolean currentlyLfg, @Nullable String lfgMessage) {
        if (!currentlyLfg) {
            return "You are not looking for a group.";
        }
        return lfgMessage == null || lfgMessage.isBlank()
            ? "You are looking for a group."
            : "You are looking for a group: " + lfgMessage;
    }

    /**
     * Formats the {@code WHO}-roster suffix for a player flagged looking-for-group.
     *
     * @param lfg        whether the player is currently flagged looking-for-group
     * @param lfgMessage the player's custom LFG note, or {@code null}/blank for the default
     * @return {@code " [LFG]"}, {@code " [LFG: <note>]"} when a note is set, or {@code ""} when not LFG
     */
    static String rosterTag(boolean lfg, @Nullable String lfgMessage) {
        if (!lfg) {
            return "";
        }
        return lfgMessage == null || lfgMessage.isBlank()
            ? " [LFG]"
            : " [LFG: " + lfgMessage + "]";
    }
}

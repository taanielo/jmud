package io.taanielo.jmud.core.guild;

import org.jspecify.annotations.Nullable;

/**
 * Outcome of a {@link GuildService} operation, carrying a success flag, a player-facing message, and
 * the affected guild snapshot (when relevant) so the caller can notify members or update player
 * records without re-querying.
 *
 * @param success {@code true} when the operation succeeded
 * @param message the message to show the invoking player
 * @param guild   the affected guild snapshot, or {@code null} when not applicable (e.g. failures, or a
 *                disband where the guild no longer exists — see {@link #guild()} semantics per method)
 */
public record GuildResult(boolean success, String message, @Nullable Guild guild) {

    /** Creates a failed result with the given message and no guild. */
    public static GuildResult failure(String message) {
        return new GuildResult(false, message, null);
    }

    /** Creates a successful result with the given message and guild snapshot. */
    public static GuildResult success(String message, Guild guild) {
        return new GuildResult(true, message, guild);
    }
}

package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Represents a socket command that can match input and execute against a context.
 */
public interface SocketCommandHandler {

    /**
     * Returns the display name of the command for help and ambiguity messages.
     */
    String name();

    /**
     * Returns a one-line description shown in the {@code HELP} command listing.
     *
     * <p>Implementors should override this to provide a meaningful summary.
     * The default returns an empty string so existing commands need no changes.
     */
    default String shortDescription() {
        return "";
    }

    /**
     * Returns a multi-line description shown when the player asks for
     * {@code HELP <name>}.
     *
     * <p>Implementors should override this to explain usage and aliases.
     * The default falls back to the {@link #shortDescription()}.
     */
    default String longDescription() {
        return shortDescription();
    }

    /**
     * Attempts to match the raw input and produce an executable match.
     */
    Optional<SocketCommandMatch> match(String input);
}

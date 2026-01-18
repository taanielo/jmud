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
     * Attempts to match the raw input and produce an executable match.
     */
    Optional<SocketCommandMatch> match(String input);
}

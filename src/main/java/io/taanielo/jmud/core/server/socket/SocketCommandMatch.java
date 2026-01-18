package io.taanielo.jmud.core.server.socket;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Executable command match produced by a SocketCommandHandler.
 */
public record SocketCommandMatch(SocketCommandHandler command, Consumer<SocketCommandContext> action) {
    public SocketCommandMatch {
        Objects.requireNonNull(command, "Command is required");
        Objects.requireNonNull(action, "Command action is required");
    }

    /**
     * Executes the matched command action against the provided context.
     */
    public void execute(SocketCommandContext context) {
        action.accept(context);
    }
}

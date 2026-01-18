package io.taanielo.jmud.core.server.socket;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Registry for socket command handlers.
 */
public class SocketCommandRegistry {
    private final List<SocketCommandHandler> commands = new ArrayList<>();

    /**
     * Registers a command handler.
     */
    public void register(SocketCommandHandler command) {
        commands.add(Objects.requireNonNull(command, "Command is required"));
    }

    /**
     * Returns a snapshot of registered commands.
     */
    public List<SocketCommandHandler> commands() {
        return List.copyOf(commands);
    }
}

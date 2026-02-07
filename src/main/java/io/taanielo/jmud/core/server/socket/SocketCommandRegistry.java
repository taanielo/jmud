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
     * Creates a registry with the default socket command set.
     */
    public static SocketCommandRegistry createDefault() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        new LookCommand(registry);
        new MoveCommand(registry);
        new GetCommand(registry);
        new DropCommand(registry);
        new QuaffCommand(registry);
        new SayCommand(registry);
        new AbilityCommand(registry);
        new AttackCommand(registry);
        new AnsiCommand(registry);
        new QuitCommand(registry);
        return registry;
    }

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

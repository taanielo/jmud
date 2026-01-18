package io.taanielo.jmud.core.server.socket;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Dispatches socket command input to registered command handlers.
 */
public class SocketCommandDispatcher {
    private final SocketCommandRegistry registry;

    /**
     * Creates a dispatcher that reads commands from the provided registry.
     */
    public SocketCommandDispatcher(SocketCommandRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "Command registry is required");
    }

    /**
     * Parses the incoming line and executes the appropriate command.
     */
    public void dispatch(SocketCommandContext context, String clientInput) {
        Objects.requireNonNull(context, "Command context is required");
        if (clientInput == null) {
            return;
        }
        String trimmed = clientInput.trim();
        if (trimmed.isEmpty()) {
            context.sendPrompt();
            return;
        }
        List<SocketCommandMatch> matches = new ArrayList<>();
        for (SocketCommandHandler command : registry.commands()) {
            command.match(trimmed).ifPresent(matches::add);
        }
        if (matches.isEmpty()) {
            context.writeLineWithPrompt("Unknown command");
            return;
        }
        if (matches.size() > 1) {
            String options = matches.stream()
                .map(match -> match.command().name())
                .distinct()
                .collect(Collectors.joining(", "));
            context.writeLineWithPrompt("Ambiguous command. Specify: " + options);
            return;
        }
        matches.getFirst().execute(context);
    }
}

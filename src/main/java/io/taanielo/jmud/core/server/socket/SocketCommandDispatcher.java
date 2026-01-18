package io.taanielo.jmud.core.server.socket;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import io.taanielo.jmud.command.CommandRegistry;
import io.taanielo.jmud.core.world.Direction;

/**
 * Dispatches socket command input to registered command handlers.
 */
public class SocketCommandDispatcher {
    private final List<SocketCommandHandler> commands = new ArrayList<>();

    /**
     * Registers default socket commands.
     */
    public SocketCommandDispatcher() {
        register(new LookCommand());
        register(new MoveCommand());
        register(new SayCommand());
        register(new AbilityCommand());
        register(new AnsiCommand());
        register(new QuitCommand());
    }

    /**
     * Registers a command handler.
     */
    public void register(SocketCommandHandler command) {
        commands.add(Objects.requireNonNull(command, "Command is required"));
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
        for (SocketCommandHandler command : commands) {
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

    private static class LookCommand implements SocketCommandHandler {
        @Override
        public String name() {
            return "look";
        }

        @Override
        public Optional<SocketCommandMatch> match(String input) {
            String token = firstToken(input);
            if ("LOOK".equals(token) || "L".equals(token)) {
                return Optional.of(new SocketCommandMatch(this, SocketCommandContext::sendLook));
            }
            return Optional.empty();
        }
    }

    private static class MoveCommand implements SocketCommandHandler {
        @Override
        public String name() {
            return "move";
        }

        @Override
        public Optional<SocketCommandMatch> match(String input) {
            String[] parts = splitInput(input);
            String token = parts[0];
            Optional<Direction> direct = Direction.fromInput(token);
            if (direct.isPresent()) {
                return Optional.of(new SocketCommandMatch(this, context -> context.sendMove(direct.get())));
            }
            if (token.equals("MOVE") || token.equals("GO") || token.equals("WALK")) {
                Optional<Direction> parsed = Direction.fromInput(parts[1]);
                return parsed.map(direction ->
                    new SocketCommandMatch(this, context -> context.sendMove(direction))
                );
            }
            return Optional.empty();
        }
    }

    private static class SayCommand implements SocketCommandHandler {
        @Override
        public String name() {
            return "say";
        }

        @Override
        public Optional<SocketCommandMatch> match(String input) {
            String[] parts = splitInput(input);
            if (!"SAY".equals(parts[0])) {
                return Optional.empty();
            }
            String message = parts[1];
            return Optional.of(new SocketCommandMatch(this, context -> handleSay(context, message)));
        }

        private void handleSay(SocketCommandContext context, String message) {
            if (!context.isAuthenticated() || context.getPlayer() == null) {
                context.writeLineWithPrompt("You must be logged in to speak.");
                return;
            }
            if (message.isEmpty()) {
                context.writeLineWithPrompt("Say what?");
                return;
            }
            CommandRegistry.SAY.act().message(context.getPlayer().getUsername(), message, context.clients());
            context.sendPrompt();
        }
    }

    private static class AbilityCommand implements SocketCommandHandler {
        @Override
        public String name() {
            return "use";
        }

        @Override
        public Optional<SocketCommandMatch> match(String input) {
            String[] parts = splitInput(input);
            String token = parts[0];
            if (!"CAST".equals(token) && !"USE".equals(token)) {
                return Optional.empty();
            }
            String args = parts[1];
            return Optional.of(new SocketCommandMatch(this, context -> context.useAbility(args)));
        }
    }

    private static class AnsiCommand implements SocketCommandHandler {
        @Override
        public String name() {
            return "ansi";
        }

        @Override
        public Optional<SocketCommandMatch> match(String input) {
            String[] parts = splitInput(input);
            if (!"ANSI".equals(parts[0])) {
                return Optional.empty();
            }
            String args = parts[1];
            return Optional.of(new SocketCommandMatch(this, context -> context.updateAnsi(args)));
        }
    }

    private static class QuitCommand implements SocketCommandHandler {
        @Override
        public String name() {
            return "quit";
        }

        @Override
        public Optional<SocketCommandMatch> match(String input) {
            String token = firstToken(input);
            if ("QUIT".equals(token)) {
                return Optional.of(new SocketCommandMatch(this, context -> CommandRegistry.QUIT.act().input(context)));
            }
            return Optional.empty();
        }
    }

    private static String firstToken(String input) {
        return splitInput(input)[0];
    }

    private static String[] splitInput(String input) {
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return new String[] {"", ""};
        }
        String[] parts = trimmed.split("\\s+", 2);
        String token = parts[0].toUpperCase(Locale.ROOT);
        String args = parts.length > 1 ? parts[1].trim() : "";
        return new String[] {token, args};
    }
}

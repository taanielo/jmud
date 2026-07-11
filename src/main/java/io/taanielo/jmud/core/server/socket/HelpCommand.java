package io.taanielo.jmud.core.server.socket;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Handles the {@code HELP} / {@code H} command, giving players a way to
 * discover all available commands and their descriptions.
 *
 * <p>Two forms are supported:
 * <ul>
 *   <li>{@code HELP}       — prints a sorted list of all registered command names
 *       together with their {@link SocketCommandHandler#shortDescription()}.</li>
 *   <li>{@code HELP <name>} — prints the {@link SocketCommandHandler#longDescription()}
 *       for the named command, or an error if no matching command is found.</li>
 * </ul>
 *
 * <p>The command list is derived from the registry snapshot at the time the
 * command is executed, so newly registered commands are visible immediately.
 */
public class HelpCommand extends RegistrableCommand {

    private final SocketCommandRegistry registry;

    /**
     * Creates a {@code HelpCommand} and registers it with the given registry.
     *
     * @param registry the registry that this command is part of
     */
    public HelpCommand(SocketCommandRegistry registry) {
        super(registry);
        this.registry = registry;
    }

    @Override
    public String name() {
        return "help";
    }

    @Override
    public String shortDescription() {
        return "List available commands or describe a specific command. Aliases: H";
    }

    @Override
    public String longDescription() {
        return """
               Usage: HELP  |  HELP <command>
                 HELP          \u2014 show a sorted list of all commands with short descriptions.
                 HELP <name>   \u2014 show the full description for the named command.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        String token = parts[0];
        if (!"HELP".equals(token) && !"H".equals(token)) {
            return Optional.empty();
        }
        String arg = parts[1].trim();
        if (arg.isBlank()) {
            return Optional.of(new SocketCommandMatch(this, this::handleListAll));
        }
        String target = arg;
        return Optional.of(new SocketCommandMatch(this, context -> handleDetail(context, target)));
    }

    /**
     * Sends a sorted list of all commands with their short descriptions.
     */
    private void handleListAll(SocketCommandContext context) {
        List<SocketCommandHandler> sorted = registry.commands().stream()
                .sorted(Comparator.comparing(SocketCommandHandler::name))
                .toList();

        context.writeLineSafe("Available commands:");
        for (SocketCommandHandler handler : sorted) {
            String short_ = handler.shortDescription();
            if (short_.isBlank()) {
                context.writeLineSafe("  " + handler.name());
            } else {
                context.writeLineSafe(String.format("  %-12s %s", handler.name(), short_));
            }
        }
        context.sendPrompt();
    }

    /**
     * Sends the long description for the named command, or an error message.
     *
     * @param context the command execution context
     * @param name    the command name to look up (case-insensitive)
     */
    private void handleDetail(SocketCommandContext context, String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        Optional<SocketCommandHandler> found = registry.commands().stream()
                .filter(h -> h.name().equalsIgnoreCase(lower))
                .findFirst();

        if (found.isEmpty()) {
            context.writeLineWithPrompt("No help available for '" + name + "'.");
            return;
        }
        SocketCommandHandler handler = found.get();
        String desc = handler.longDescription();
        if (desc.isBlank()) {
            context.writeLineWithPrompt("No detailed help available for '" + handler.name() + "'.");
            return;
        }
        for (String line : desc.split("\n", -1)) {
            context.writeLineSafe(line);
        }
        context.sendPrompt();
    }
}

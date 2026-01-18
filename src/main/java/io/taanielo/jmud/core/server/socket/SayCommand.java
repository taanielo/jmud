package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

import io.taanielo.jmud.command.CommandRegistry;

/**
 * Handles say commands.
 */
public class SayCommand extends RegistrableCommand {
    public SayCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "say";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
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

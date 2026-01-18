package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles ANSI toggle commands.
 */
public class AnsiCommand extends RegistrableCommand {
    public AnsiCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "ansi";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"ANSI".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.updateAnsi(args)));
    }
}

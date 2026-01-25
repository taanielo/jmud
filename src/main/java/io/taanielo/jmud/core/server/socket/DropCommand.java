package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles drop commands.
 */
public class DropCommand extends RegistrableCommand {
    public DropCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "drop";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"DROP".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.dropItem(args)));
    }
}

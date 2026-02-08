package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles unequip commands.
 */
public class UnequipCommand extends RegistrableCommand {
    public UnequipCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "unequip";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"UNEQUIP".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.unequipItem(args)));
    }
}

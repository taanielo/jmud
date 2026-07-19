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
    public String shortDescription() {
        return "Unequip a worn item back to your inventory. Alias: REMOVE";
    }

    @Override
    public String longDescription() {
        return """
               Usage: UNEQUIP <item>
                 Removes a currently equipped item and returns it to your inventory.
                 Alias: REMOVE.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        String verb = parts[0];
        if (!"UNEQUIP".equals(verb) && !"REMOVE".equals(verb)) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.unequipItem(args)));
    }
}

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
    public String shortDescription() {
        return "Drop an item from your inventory onto the room floor.";
    }

    @Override
    public String longDescription() {
        return """
               Usage: DROP <item>
                 Drops the named item from your inventory onto the floor of your
                 current room, unequipping it first if worn.
               Usage: DROP ALL
                 Drops every unequipped item in your inventory in one command.
                 Worn or wielded gear is left alone; UNEQUIP it first to drop it.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"DROP".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        if (args.trim().equalsIgnoreCase("all")) {
            return Optional.of(new SocketCommandMatch(this, SocketCommandContext::dropAllItems));
        }
        return Optional.of(new SocketCommandMatch(this, context -> context.dropItem(args)));
    }
}

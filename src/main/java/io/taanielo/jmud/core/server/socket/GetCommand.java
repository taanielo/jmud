package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles get commands.
 */
public class GetCommand extends RegistrableCommand {
    public GetCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "get";
    }

    @Override
    public String shortDescription() {
        return "Pick up an item from the room floor.";
    }

    @Override
    public String longDescription() {
        return "Usage: GET <item>\n"
             + "  Picks up the named item from the floor of your current room\n"
             + "  and adds it to your inventory.";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"GET".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.getItem(args)));
    }
}

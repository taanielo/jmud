package io.taanielo.jmud.core.server.socket;

import java.util.Locale;
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
        return """
               Usage: GET <item>
                 Picks up the named item from the floor of your current room
                 and adds it to your inventory.
               Usage: GET ALL
                 Picks up every item on the room floor in one command, stopping
                 early (and keeping what you already took) if you become
                 overburdened.
               Usage: GET ALL FROM <container>
                 Empties a container you are carrying, moving all of its contents
                 into your inventory.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"GET".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        // "GET <item> FROM <container>" (including "GET ALL FROM <container>") is handled by
        // GetFromCommand; don't double-match it here.
        if (args.toLowerCase(Locale.ROOT).contains(GetFromCommand.SEPARATOR)) {
            return Optional.empty();
        }
        if (args.trim().equalsIgnoreCase("all")) {
            return Optional.of(new SocketCommandMatch(this, SocketCommandContext::getAllItems));
        }
        return Optional.of(new SocketCommandMatch(this, context -> context.getItem(args)));
    }
}

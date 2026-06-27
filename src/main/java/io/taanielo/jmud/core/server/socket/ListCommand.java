package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code LIST} command, showing the shop's inventory in the current room.
 *
 * <p>If there is no shop in the current room, an appropriate error is printed.
 */
public class ListCommand extends RegistrableCommand {

    public ListCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "list";
    }

    @Override
    public String shortDescription() {
        return "List a nearby shop's inventory and prices.";
    }

    @Override
    public String longDescription() {
        return "Usage: LIST\n"
             + "  When a shopkeeper is present in the room, shows available wares with prices.\n"
             + "  Prints an error if there is no shop here.";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String token = SocketCommandParsing.firstToken(input);
        if (!"LIST".equals(token)) {
            return Optional.empty();
        }
        return Optional.of(new SocketCommandMatch(this, SocketCommandContext::listShopInventory));
    }
}

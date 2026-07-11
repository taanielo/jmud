package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code BUY <item>} command, purchasing an item from the shop in the current room.
 */
public class BuyCommand extends RegistrableCommand {

    public BuyCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "buy";
    }

    @Override
    public String shortDescription() {
        return "Buy an item from the shop in the current room.";
    }

    @Override
    public String longDescription() {
        return """
               Usage: BUY <item name>
                 Purchases the named item from the shopkeeper. Deducts the item's price from
                 your gold. Use LIST to see what is available and the prices.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"BUY".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.buyFromShop(args)));
    }
}

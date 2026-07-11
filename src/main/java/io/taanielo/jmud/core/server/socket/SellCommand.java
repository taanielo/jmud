package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code SELL <item>} command, selling an item from the player's
 * inventory to the shop in the current room.
 */
public class SellCommand extends RegistrableCommand {

    public SellCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "sell";
    }

    @Override
    public String shortDescription() {
        return "Sell an item from your inventory to the shop in the current room.";
    }

    @Override
    public String longDescription() {
        return """
               Usage: SELL <item name>
                 Sells the named item from your inventory to the shopkeeper for a fraction
                 of its base value (typically 50%). The item is removed from your inventory
                 and you receive gold in return.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"SELL".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.sellToShop(args)));
    }
}

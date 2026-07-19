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
                      SELL ALL [keyword]
                 Sells the named item from your inventory to the shopkeeper for a fraction
                 of its base value (typically 50%). The item is removed from your inventory
                 and you receive gold in return.
                 SELL ALL sells every item in your inventory in one command, reporting the
                 number sold and total gold earned. SELL ALL <keyword> only sells inventory
                 items whose name contains the keyword, so you can dump junk while keeping
                 quest items and reagents. Equipped gear is never sold.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"SELL".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        String[] argParts = SocketCommandParsing.splitInput(args);
        if ("ALL".equalsIgnoreCase(argParts[0])) {
            String keyword = argParts[1].isBlank() ? null : argParts[1].trim();
            return Optional.of(new SocketCommandMatch(this, context -> context.sellAllToShop(keyword)));
        }
        return Optional.of(new SocketCommandMatch(this, context -> context.sellToShop(args)));
    }
}

package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code DRINK} command, allowing players to consume drink items from their
 * inventory to restore thirst.
 *
 * <p>Accepted tokens: {@code DRINK} and the abbreviation {@code DR}.
 */
public class DrinkCommand extends RegistrableCommand {
    public DrinkCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "drink";
    }

    @Override
    public String shortDescription() {
        return "Drink a beverage from your inventory to quench thirst.";
    }

    @Override
    public String longDescription() {
        return "Usage: DRINK <item>  (alias: DR)\n"
             + "  Consumes the named drink item from your inventory, restoring thirst.\n"
             + "  Drinking regularly keeps your regeneration from being penalised.";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        String token = parts[0];
        if (!"DRINK".equals(token) && !"DR".equals(token)) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.drinkItem(args)));
    }
}

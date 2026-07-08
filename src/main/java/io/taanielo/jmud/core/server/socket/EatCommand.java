package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code EAT} command, allowing players to consume food items from their
 * inventory to restore hunger.
 *
 * <p>Accepted token: {@code EAT}.
 */
public class EatCommand extends RegistrableCommand {
    public EatCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "eat";
    }

    @Override
    public String shortDescription() {
        return "Eat food from your inventory to satisfy hunger.";
    }

    @Override
    public String longDescription() {
        return "Usage: EAT <item>\n"
             + "  Consumes the named food item from your inventory, restoring hunger.\n"
             + "  Eating regularly keeps your regeneration from being penalised.";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        String token = parts[0];
        if (!"EAT".equals(token)) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.eatItem(args)));
    }
}

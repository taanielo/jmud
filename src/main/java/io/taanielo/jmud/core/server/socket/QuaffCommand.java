package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles quaff commands, allowing players to consume potions and other drinkable items.
 *
 * <p>Accepted tokens: {@code QUAFF} and the abbreviation {@code QU}.
 */
public class QuaffCommand extends RegistrableCommand {
    public QuaffCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "quaff";
    }

    @Override
    public String shortDescription() {
        return "Drink a potion from your inventory.";
    }

    @Override
    public String longDescription() {
        return "Usage: QUAFF <item>  (alias: QU)\n"
             + "  Consumes the named item from your inventory, applying its effects\n"
             + "  immediately. Healing potions restore HP; poisonous potions deal damage.";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        String token = parts[0];
        if (!"QUAFF".equals(token) && !"QU".equals(token)) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.quaffItem(args)));
    }
}

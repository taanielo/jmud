package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code STORE <item name>} command, moving an item from carried inventory
 * into the player's personal bank vault.
 */
public class StoreCommand extends RegistrableCommand {

    public StoreCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "store";
    }

    @Override
    public String shortDescription() {
        return "Store an item in your bank vault for safe keeping.";
    }

    @Override
    public String longDescription() {
        return """
               Usage: STORE <item name>
                 Moves the named item from your inventory into your bank vault, unequipping it first
                 if worn. Vaulted items are safe from death, corpse decay and looting. The vault has a
                 limited number of slots. Requires a bank NPC in the room.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"STORE".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.storeItemInBank(args)));
    }
}

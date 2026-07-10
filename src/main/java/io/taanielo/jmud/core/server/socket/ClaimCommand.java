package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code CLAIM <item name>} command, moving an item from the player's bank vault
 * back into carried inventory.
 */
public class ClaimCommand extends RegistrableCommand {

    public ClaimCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "claim";
    }

    @Override
    public String shortDescription() {
        return "Claim an item back from your bank vault.";
    }

    @Override
    public String longDescription() {
        return "Usage: CLAIM <item name>\n"
             + "  Moves the named item from your bank vault back into your inventory. Fails if carrying\n"
             + "  it would exceed your carry weight — lighten your load first. Requires a bank NPC in the\n"
             + "  room.";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"CLAIM".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.claimItemFromBank(args)));
    }
}

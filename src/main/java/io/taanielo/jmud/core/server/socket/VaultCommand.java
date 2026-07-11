package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code VAULT} command, listing the items a player currently has stored
 * in their personal bank vault.
 */
public class VaultCommand extends RegistrableCommand {

    public VaultCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "vault";
    }

    @Override
    public String shortDescription() {
        return "List the items stored in your bank vault.";
    }

    @Override
    public String longDescription() {
        return """
               Usage: VAULT
                 Lists the items currently stored in your bank vault, with their weights and the number
                 of slots used. Requires a bank NPC in the room.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String token = SocketCommandParsing.firstToken(input);
        if (!"VAULT".equals(token)) {
            return Optional.empty();
        }
        return Optional.of(new SocketCommandMatch(this, SocketCommandContext::sendVault));
    }
}

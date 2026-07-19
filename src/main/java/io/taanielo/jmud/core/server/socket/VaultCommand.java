package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code VAULT} command family for a player's personal bank vault.
 *
 * <p>Sub-commands:
 * <ul>
 *   <li>{@code VAULT}         — list the items stored in your vault, with usage vs. capacity</li>
 *   <li>{@code VAULT UPGRADE} — pay gold to permanently expand your vault capacity</li>
 * </ul>
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
        return "List the items stored in your bank vault. Use VAULT UPGRADE to expand it.";
    }

    @Override
    public String longDescription() {
        return """
               Usage: VAULT [UPGRADE]
                 VAULT          Lists the items currently stored in your bank vault, with their weights,
                                the slots used vs. your effective capacity, and the cost of the next
                                upgrade tier when you are not already maxed.
                 VAULT UPGRADE  Permanently pays gold to expand your personal vault above the default
                                30 slots, in escalating tiers:
                                  tier 1 — 40 slots for 5,000 gold
                                  tier 2 — 50 slots for 15,000 gold
                                  tier 3 — 60 slots for 40,000 gold (max)
                                Purchased capacity is never lost. Requires a bank NPC in the room.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"VAULT".equals(parts[0])) {
            return Optional.empty();
        }
        if ("UPGRADE".equalsIgnoreCase(parts[1].trim())) {
            return Optional.of(new SocketCommandMatch(this, SocketCommandContext::upgradeVault));
        }
        return Optional.of(new SocketCommandMatch(this, SocketCommandContext::sendVault));
    }
}

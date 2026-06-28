package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code DEPOSIT <amount>} command, moving gold from carried balance to the bank.
 */
public class DepositCommand extends RegistrableCommand {

    public DepositCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "deposit";
    }

    @Override
    public String shortDescription() {
        return "Deposit gold into the bank for safe keeping.";
    }

    @Override
    public String longDescription() {
        return "Usage: DEPOSIT <amount>\n"
             + "  Moves the specified amount of gold from your carried balance to the bank.\n"
             + "  Banked gold is safe from death and never lost. Requires a bank NPC in the room.";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"DEPOSIT".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.depositToBank(args)));
    }
}

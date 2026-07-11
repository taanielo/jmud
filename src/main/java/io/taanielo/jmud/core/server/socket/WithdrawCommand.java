package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code WITHDRAW <amount>} command, moving gold from the bank to carried balance.
 */
public class WithdrawCommand extends RegistrableCommand {

    public WithdrawCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "withdraw";
    }

    @Override
    public String shortDescription() {
        return "Withdraw gold from the bank into your pocket.";
    }

    @Override
    public String longDescription() {
        return """
               Usage: WITHDRAW <amount>
                 Moves the specified amount of gold from the bank to your carried balance.
                 Requires a bank NPC in the room.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"WITHDRAW".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.withdrawFromBank(args)));
    }
}

package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles ability usage commands.
 */
public class AbilityCommand extends RegistrableCommand {
    public AbilityCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "use";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        String token = parts[0];
        if (!"CAST".equals(token) && !"USE".equals(token)) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.useAbility(args)));
    }
}

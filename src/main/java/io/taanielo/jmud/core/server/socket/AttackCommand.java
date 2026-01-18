package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles attack commands.
 */
public class AttackCommand extends RegistrableCommand {
    public AttackCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "attack";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        String token = parts[0];
        if (!"ATTACK".equals(token)) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.executeAttack(args)));
    }
}

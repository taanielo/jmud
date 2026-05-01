package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles kill commands targeting mobs in the same room.
 */
public class KillCommand extends RegistrableCommand {
    public KillCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "kill";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        String token = parts[0];
        if (!"KILL".equals(token) && !"K".equals(token)) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.killMob(args)));
    }
}

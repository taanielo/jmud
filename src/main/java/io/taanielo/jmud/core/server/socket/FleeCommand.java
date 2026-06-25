package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code FLEE} command, allowing a player to escape from active combat
 * by moving to a randomly chosen available exit.
 */
public class FleeCommand extends RegistrableCommand {

    public FleeCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "flee";
    }

    @Override
    public String shortDescription() {
        return "Escape from combat by fleeing to a random exit. Aliases: FL";
    }

    @Override
    public String longDescription() {
        return "Usage: FLEE  |  FL\n"
             + "  Attempts to escape from active combat by moving to a randomly chosen exit.\n"
             + "  You must be in combat to flee. The direction taken is chosen at random.";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String token = SocketCommandParsing.splitInput(input)[0];
        if (!"FLEE".equals(token) && !"FL".equals(token)) {
            return Optional.empty();
        }
        return Optional.of(new SocketCommandMatch(this, SocketCommandContext::fleeCombat));
    }
}

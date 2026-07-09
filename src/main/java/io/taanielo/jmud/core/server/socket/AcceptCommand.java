package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code ACCEPT} command, which engages a pending consensual duel challenge.
 */
public class AcceptCommand extends RegistrableCommand {

    public AcceptCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "accept";
    }

    @Override
    public String shortDescription() {
        return "Accept a pending duel challenge.";
    }

    @Override
    public String longDescription() {
        return "Usage: ACCEPT\n"
             + "  Accepts a pending duel challenge issued to you with DUEL, engaging both players in\n"
             + "  combat. Challenges expire 30 seconds after they are issued.";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String token = SocketCommandParsing.splitInput(input)[0];
        if (!"ACCEPT".equals(token)) {
            return Optional.empty();
        }
        return Optional.of(new SocketCommandMatch(this, SocketCommandContext::acceptDuel));
    }
}

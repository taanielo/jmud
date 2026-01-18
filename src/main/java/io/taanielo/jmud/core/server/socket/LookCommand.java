package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles look commands.
 */
public class LookCommand extends RegistrableCommand {
    public LookCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "look";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String token = SocketCommandParsing.firstToken(input);
        if ("LOOK".equals(token) || "L".equals(token)) {
            return Optional.of(new SocketCommandMatch(this, SocketCommandContext::sendLook));
        }
        return Optional.empty();
    }
}

package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

import io.taanielo.jmud.command.CommandRegistry;

/**
 * Handles quit commands.
 */
public class QuitCommand extends RegistrableCommand {
    public QuitCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "quit";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String token = SocketCommandParsing.firstToken(input);
        if ("QUIT".equals(token)) {
            return Optional.of(new SocketCommandMatch(this, context -> CommandRegistry.QUIT.act().input(context)));
        }
        return Optional.empty();
    }
}

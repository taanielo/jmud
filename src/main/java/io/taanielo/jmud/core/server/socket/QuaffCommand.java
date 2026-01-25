package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles quaff commands.
 */
public class QuaffCommand extends RegistrableCommand {
    public QuaffCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "quaff";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"QUAFF".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.quaffItem(args)));
    }
}

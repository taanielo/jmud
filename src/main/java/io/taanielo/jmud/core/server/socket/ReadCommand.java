package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles read commands, allowing players to read scrolls from their inventory to permanently
 * learn a new ability.
 *
 * <p>Accepted tokens: {@code READ} and the abbreviation {@code REA}.
 */
public class ReadCommand extends RegistrableCommand {
    public ReadCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "read";
    }

    @Override
    public String shortDescription() {
        return "Read a scroll from your inventory to learn its ability.";
    }

    @Override
    public String longDescription() {
        return "Usage: READ <item>  (alias: REA)\n"
             + "  Reads the named scroll from your inventory, permanently teaching you\n"
             + "  the ability it references. The scroll is consumed on success.";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        String token = parts[0];
        if (!"READ".equals(token) && !"REA".equals(token)) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.readItem(args)));
    }
}

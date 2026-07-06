package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles write commands, allowing players to inscribe a scroll for an ability they already
 * know, adding the new scroll to their inventory.
 *
 * <p>Accepted tokens: {@code WRITE} and the abbreviation {@code WRI}.
 */
public class WriteCommand extends RegistrableCommand {
    public WriteCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "write";
    }

    @Override
    public String shortDescription() {
        return "Inscribe a scroll for an ability you know.";
    }

    @Override
    public String longDescription() {
        return "Usage: WRITE <ability>  (alias: WRI)\n"
             + "  Inscribes a new scroll for the named ability, provided you already know\n"
             + "  it, and adds the scroll to your inventory.";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        String token = parts[0];
        if (!"WRITE".equals(token) && !"WRI".equals(token)) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.writeItem(args)));
    }
}

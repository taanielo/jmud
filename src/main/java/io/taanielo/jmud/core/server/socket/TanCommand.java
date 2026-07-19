package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code TAN} command. With no arguments it lists the leather armor recipes a
 * leatherworker can make, with live {@code have/need} material counts and gold cost; with an item
 * argument it attempts to craft that recipe, consuming the required pelts, fangs and gold. Requires a
 * leatherworker to be present in the current room, mirroring {@link CraftCommand}.
 */
public class TanCommand extends RegistrableCommand {

    public TanCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "tan";
    }

    @Override
    public String shortDescription() {
        return "Tan leather armor from beast pelts at a leatherworker.";
    }

    @Override
    public String longDescription() {
        return """
               Usage: TAN [item name]
                 With no argument, lists the leather armor recipes the leatherworker in this room can
                 make, showing the pelts and fangs each needs (and how many you are carrying) plus the
                 gold cost. With an item name, tans it: the listed materials and gold are consumed and
                 the finished piece is added to your inventory. You must be standing near a
                 leatherworker. Better pieces require a higher leatherworking level, which grows each
                 time you tan.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"TAN".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.tan(args)));
    }
}

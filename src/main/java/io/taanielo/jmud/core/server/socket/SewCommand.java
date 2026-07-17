package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code SEW} command. With no arguments it lists the cloth armor recipes a tailor can
 * make, with live {@code have/need} material counts, min-skill gates and gold cost; with an item
 * argument it attempts to craft that recipe, consuming the required cloth and gold. Requires a tailor
 * to be present in the current room, mirroring {@link CutCommand}.
 */
public class SewCommand extends RegistrableCommand {

    public SewCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "sew";
    }

    @Override
    public String shortDescription() {
        return "Sew cloth caster armor from silk and linen at a tailor.";
    }

    @Override
    public String longDescription() {
        return """
               Usage: SEW [item name]
                 With no argument, lists the cloth armor recipes the tailor in this room can make,
                 showing the cloth each needs (and how much you are carrying) plus the gold cost. With
                 an item name, sews it: the listed cloth and gold are consumed and the finished
                 garment is added to your inventory. You must be standing near a tailor. Better pieces
                 require a higher tailoring level, which grows each time you sew.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"SEW".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.sew(args)));
    }
}

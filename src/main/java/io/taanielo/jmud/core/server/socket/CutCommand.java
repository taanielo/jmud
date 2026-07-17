package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code CUT} command. With no arguments it lists the ring and necklace recipes a
 * jeweler can make, with live {@code have/need} material counts, min-skill gates and gold cost; with
 * an item argument it attempts to craft that recipe, consuming the required raw gems and gold.
 * Requires a jeweler to be present in the current room, mirroring {@link TanCommand}.
 */
public class CutCommand extends RegistrableCommand {

    public CutCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "cut";
    }

    @Override
    public String shortDescription() {
        return "Cut rings and necklaces from raw gems at a jeweler.";
    }

    @Override
    public String longDescription() {
        return """
               Usage: CUT [item name]
                 With no argument, lists the ring and necklace recipes the jeweler in this room can
                 make, showing the raw gems each needs (and how many you are carrying) plus the gold
                 cost. With an item name, cuts it: the listed gems and gold are consumed and the
                 finished accessory is added to your inventory. You must be standing near a jeweler.
                 Better pieces require a higher jewelcrafting level, which grows each time you cut.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"CUT".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.cut(args)));
    }
}

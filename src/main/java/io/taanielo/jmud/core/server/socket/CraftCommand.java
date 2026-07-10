package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code CRAFT} command. With no arguments it lists the recipes a blacksmith can make,
 * with live {@code have/need} material counts and gold cost; with an item argument it attempts to
 * craft that recipe, consuming the required materials and gold. Requires a blacksmith to be present
 * in the current room, mirroring {@link RepairCommand}.
 */
public class CraftCommand extends RegistrableCommand {

    public CraftCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "craft";
    }

    @Override
    public String shortDescription() {
        return "Craft gear from gathered materials at a blacksmith.";
    }

    @Override
    public String longDescription() {
        return "Usage: CRAFT [item name]\n"
             + "  With no argument, lists the recipes the blacksmith in this room can make, showing\n"
             + "  the materials each needs (and how many you are carrying) plus the gold cost.\n"
             + "  With an item name, crafts it: the listed materials and gold are consumed and the\n"
             + "  finished item is added to your inventory. You must be standing near a blacksmith.";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"CRAFT".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.craft(args)));
    }
}

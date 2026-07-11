package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code BREW} command. With no arguments it lists the potion recipes an alchemist can
 * make, with live {@code have/need} herb counts and gold cost; with a potion argument it attempts to
 * brew that recipe, consuming the required herbs and gold. Requires an alchemist to be present in the
 * current room, mirroring {@link CraftCommand}.
 */
public class BrewCommand extends RegistrableCommand {

    public BrewCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "brew";
    }

    @Override
    public String shortDescription() {
        return "Brew potions from gathered herbs at an alchemist.";
    }

    @Override
    public String longDescription() {
        return """
               Usage: BREW [potion name]
                 With no argument, lists the potion recipes the alchemist in this room can make,
                 showing the herbs each needs (and how many you are carrying) plus the gold cost.
                 With a potion name, brews it: the listed herbs and gold are consumed and the
                 finished potion is added to your inventory. You must be standing near an alchemist.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"BREW".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.brew(args)));
    }
}

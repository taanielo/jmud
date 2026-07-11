package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code COOK} command. With no arguments it lists the meal recipes a cook can make,
 * with live {@code have/need} ingredient counts and gold cost; with a meal argument it attempts to
 * cook that recipe, consuming the required ingredients and gold. Requires a cook to be present in
 * the current room, mirroring {@link BrewCommand}.
 */
public class CookCommand extends RegistrableCommand {

    public CookCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "cook";
    }

    @Override
    public String shortDescription() {
        return "Cook meals from raw ingredients at a cook.";
    }

    @Override
    public String longDescription() {
        return """
               Usage: COOK [meal name]
                 With no argument, lists the meal recipes the cook in this room can make,
                 showing the ingredients each needs (and how many you are carrying) plus the gold cost.
                 With a meal name, cooks it: the listed ingredients and gold are consumed and the
                 finished meal is added to your inventory. You must be standing near a cook.
                 Some meals grant a short timed buff when eaten (EAT <meal>).\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"COOK".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.cook(args)));
    }
}

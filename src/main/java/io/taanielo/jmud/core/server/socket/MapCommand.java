package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code MAP} command, showing an ASCII minimap of the rooms the player has explored
 * around their current location.
 */
public class MapCommand extends RegistrableCommand {

    public MapCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "map";
    }

    @Override
    public String shortDescription() {
        return "Show a minimap of nearby rooms you have explored.";
    }

    @Override
    public String longDescription() {
        return "Usage: MAP\n"
             + "  Draws a small ASCII minimap centred on your current room. Only rooms you have\n"
             + "  previously visited are shown: @ is your current room, # a room you have explored,\n"
             + "  and . an unexplored room adjacent to explored territory.";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String token = SocketCommandParsing.splitInput(input)[0];
        if (!"MAP".equals(token)) {
            return Optional.empty();
        }
        return Optional.of(new SocketCommandMatch(this, SocketCommandContext::sendMap));
    }
}

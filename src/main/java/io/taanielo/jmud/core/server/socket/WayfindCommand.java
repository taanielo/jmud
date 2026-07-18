package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code WAYFIND} command, printing live turn-by-turn compass directions from the
 * player's current room to a zone they name.
 *
 * <p>{@code WAYFIND <area>} routes to the destination area's waypoint (entrance) room; {@code
 * WAYFIND} with no argument lists the current area and its charted neighbours. Parsing lives here;
 * the pathfinding and messaging logic lives in {@code WayfindService} via
 * {@link SocketCommandContext#wayfind(String)}.
 */
public class WayfindCommand extends RegistrableCommand {

    public WayfindCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "wayfind";
    }

    @Override
    public String shortDescription() {
        return "Get turn-by-turn directions to an area you've visited. WAYFIND lists nearby areas.";
    }

    @Override
    public String longDescription() {
        return """
               Usage: WAYFIND  |  WAYFIND <area name or id>
                 WAYFIND with no argument lists your current area and the areas it connects to.
                 WAYFIND <area> prints the shortest walking route to that area's entrance as a list
                 of compass directions (e.g. north, north, up, east) plus the total step count.
                 Matching is case-insensitive and accepts a partial name or the area id.
                 Routing only uses exits you can already walk — regular exits and secret exits that
                 have been discovered with SEARCH. A locked door is still part of the route: the
                 directions do not fail just because a door is locked, since you carry the key
                 separately (see HELP LOCK / HELP UNLOCK). When the shortest or only way across needs
                 a ferry, the route includes a "board the <ferry> at <dock> and ride to <dock>" step:
                 walk onto the ferry's deck at that dock and wait for its scheduled departure. WAYFIND
                 gives directions, not a departure time, so it never promises when the ferry sails.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"WAYFIND".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.wayfind(args)));
    }
}

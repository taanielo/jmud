package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code AUTOWALK} command, automatically walking the player one room per tick toward a
 * charted area's waypoint.
 *
 * <p>{@code AUTOWALK <area>} resolves the destination exactly like {@code WAYFIND} (exact id, exact
 * name, then partial match) and, when the shortest route is pure walking, begins auto-walking there.
 * {@code AUTOWALK STOP} cancels an in-progress walk. Parsing lives here; the routing and walk
 * state-machine logic live in {@code WayfindService}/{@code SocketCommandContextImpl} via
 * {@link SocketCommandContext#autoWalk(String)}.
 */
public class AutoWalkCommand extends RegistrableCommand {

    public AutoWalkCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "autowalk";
    }

    @Override
    public String shortDescription() {
        return "Automatically walk to an area you've visited, one room per tick. AUTOWALK STOP cancels.";
    }

    @Override
    public String longDescription() {
        return """
               Usage: AUTOWALK <area name or id>  |  AUTOWALK STOP
                 AUTOWALK <area> works out the shortest walking route to that area's entrance — the
                 same route WAYFIND would print — and then walks you there automatically, one room per
                 game tick. Matching is case-insensitive and accepts a partial name or the area id,
                 with the same "which area did you mean?" prompt as WAYFIND when it is ambiguous.
                 The walk stops the instant you type ANY command (a manual direction always overrides
                 autopilot), if you are pulled into combat, or if a step is blocked (a locked door, no
                 move points left, or a root/stun effect) — the same reason a manual move would fail.
                 It ends when you reach the destination, showing the normal arrival room.
                 AUTOWALK STOP cancels an in-progress walk at any time.
                 Ferries are not automated: if the shortest route crosses the Coastal Ferry, AUTOWALK
                 refuses and points you at WAYFIND <area> so you can read the directions and board the
                 ferry yourself.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"AUTOWALK".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.autoWalk(args)));
    }
}

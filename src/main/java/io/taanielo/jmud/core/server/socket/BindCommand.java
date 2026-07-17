package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code BIND} command, letting a player anchor their personal recall/respawn point to
 * the waypoint (entrance) room of their current area.
 *
 * <p>{@code BIND} with no argument reports the player's current bind point (or the default,
 * Greystone Town). {@code BIND HERE} anchors the recall/respawn point to the current room when it is
 * a zone's waypoint and the player is out of combat; both {@code RECALL} and death respawn then
 * return the player here instead of the default starting town.
 */
public class BindCommand extends RegistrableCommand {

    public BindCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "bind";
    }

    @Override
    public String shortDescription() {
        return "Anchor your recall/respawn point to a zone's waypoint. BIND reports it; BIND HERE sets it.";
    }

    @Override
    public String longDescription() {
        return """
               Usage: BIND  |  BIND HERE
                 BIND with no argument reports where RECALL and death respawn currently send you
                 (Greystone Town by default). BIND HERE anchors that point to the waypoint room of
                 your current area — the room used as the zone's entrance. You must be standing in
                 that exact room and out of combat (FLEE first). Re-BIND at a later zone's waypoint to
                 move your anchor forward as you progress.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"BIND".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.bind(args)));
    }
}

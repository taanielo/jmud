package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code TRACK <mob_type>} command, the ranger-only skill for locating the nearest mob
 * of a named type anywhere in the reachable world.
 *
 * <p>The search walks the room graph outward from the ranger's current room and reports a compass
 * direction toward the closest matching mob (or that it shares the room). The game logic lives in
 * {@code GameActionService.track} via {@link SocketCommandContext#track(String)}.
 */
public class TrackCommand extends RegistrableCommand {

    public TrackCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "track";
    }

    @Override
    public String shortDescription() {
        return "Sense the direction of the nearest mob of a type (ranger skill only).";
    }

    @Override
    public String longDescription() {
        return """
               Usage: TRACK <mob type>
                 Searches the world for the nearest mob of the named type and points you toward it.
                 Rangers only.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"TRACK".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        if (args.isBlank()) {
            return Optional.of(new SocketCommandMatch(
                this, context -> context.writeLineWithPrompt("Usage: TRACK <mob type>")));
        }
        return Optional.of(new SocketCommandMatch(this, context -> context.track(args)));
    }
}

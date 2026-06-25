package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles look commands.
 */
public class LookCommand extends RegistrableCommand {
    public LookCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "look";
    }

    @Override
    public String shortDescription() {
        return "Examine your surroundings or a target. Aliases: L";
    }

    @Override
    public String longDescription() {
        return "Usage: LOOK  |  LOOK <target>\n"
             + "  LOOK        — describe the current room, its exits, and visible occupants.\n"
             + "  LOOK <name> — examine a specific player or object in the room.";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        String token = parts[0];
        if ("LOOK".equals(token) || "L".equals(token)) {
            if (parts[1].isBlank()) {
                return Optional.of(new SocketCommandMatch(this, SocketCommandContext::sendLook));
            }
            String targetInput = parts[1];
            return Optional.of(new SocketCommandMatch(this, context -> context.sendLookAt(targetInput)));
        }
        return Optional.empty();
    }
}

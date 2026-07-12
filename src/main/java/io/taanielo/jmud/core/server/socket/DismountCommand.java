package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code DISMOUNT} command, climbing the player down from their current mount and
 * returning them to normal per-step travel cost.
 *
 * <p>The game logic lives in {@code GameActionService.dismount} via
 * {@link SocketCommandContext#dismount(String)}.
 */
public class DismountCommand extends RegistrableCommand {

    public DismountCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "dismount";
    }

    @Override
    public String shortDescription() {
        return "Climb down from your mount.";
    }

    @Override
    public String longDescription() {
        return """
               Usage: DISMOUNT
                 Climbs down from the mount you are riding, returning your movement to its normal
                 move-point cost. You are also dismounted automatically when you enter combat or
                 ride into an indoor or underground room. Use MOUNT <name> to ride again.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String token = SocketCommandParsing.firstToken(input);
        if (!"DISMOUNT".equals(token)) {
            return Optional.empty();
        }
        return Optional.of(new SocketCommandMatch(this, context -> context.dismount("")));
    }
}

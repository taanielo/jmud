package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code WAKE} (or {@code STAND}) command, cancelling an active
 * resting state.
 */
public class WakeCommand extends RegistrableCommand {

    public WakeCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "wake";
    }

    @Override
    public String shortDescription() {
        return "Stand up and stop resting. Aliases: STAND";
    }

    @Override
    public String longDescription() {
        return "Usage: WAKE  |  STAND\n"
             + "  Cancels an active resting state and returns you to a standing position.";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String token = SocketCommandParsing.splitInput(input)[0];
        if (!"WAKE".equals(token) && !"STAND".equals(token)) {
            return Optional.empty();
        }
        return Optional.of(new SocketCommandMatch(this,
            context -> context.stopResting("You stand up.")));
    }
}

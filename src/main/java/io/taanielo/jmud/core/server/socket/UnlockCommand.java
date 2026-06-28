package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

import io.taanielo.jmud.core.world.Direction;

/**
 * Handles the {@code UNLOCK <direction>} command.
 *
 * <p>The player must carry the required key item. Unlocking is only possible on exits
 * that are declared lockable in the room data and are currently locked.
 */
public class UnlockCommand extends RegistrableCommand {

    public UnlockCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "unlock";
    }

    @Override
    public String shortDescription() {
        return "Unlock a door in the given direction.";
    }

    @Override
    public String longDescription() {
        return "Usage: UNLOCK <direction>\n"
             + "  Unlocks the door in the given direction. You must carry the correct key.";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"UNLOCK".equals(parts[0])) {
            return Optional.empty();
        }
        Optional<Direction> dir = Direction.fromInput(parts[1]);
        return dir.map(direction ->
            new SocketCommandMatch(this, context -> context.unlockExit(direction))
        );
    }
}

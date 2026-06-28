package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

import io.taanielo.jmud.core.world.Direction;

/**
 * Handles the {@code LOCK <direction>} command.
 *
 * <p>The player must carry the required key item. Locking is only possible on exits
 * that are declared lockable in the room data and are currently unlocked.
 */
public class LockCommand extends RegistrableCommand {

    public LockCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "lock";
    }

    @Override
    public String shortDescription() {
        return "Lock a door in the given direction.";
    }

    @Override
    public String longDescription() {
        return "Usage: LOCK <direction>\n"
             + "  Locks the door in the given direction. You must carry the correct key.";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"LOCK".equals(parts[0])) {
            return Optional.empty();
        }
        Optional<Direction> dir = Direction.fromInput(parts[1]);
        return dir.map(direction ->
            new SocketCommandMatch(this, context -> context.lockExit(direction))
        );
    }
}

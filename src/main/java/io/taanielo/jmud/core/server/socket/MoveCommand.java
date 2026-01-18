package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

import io.taanielo.jmud.core.world.Direction;

/**
 * Handles movement commands and directional aliases.
 */
public class MoveCommand extends RegistrableCommand {
    public MoveCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "move";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        String token = parts[0];
        Optional<Direction> direct = Direction.fromInput(token);
        if (direct.isPresent()) {
            return Optional.of(new SocketCommandMatch(this, context -> context.sendMove(direct.get())));
        }
        if (token.equals("MOVE") || token.equals("GO") || token.equals("WALK")) {
            Optional<Direction> parsed = Direction.fromInput(parts[1]);
            return parsed.map(direction ->
                new SocketCommandMatch(this, context -> context.sendMove(direction))
            );
        }
        return Optional.empty();
    }
}

package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code RECALL} command, teleporting the player back to the starting/town room.
 *
 * <p>Recall is blocked while the player is in active combat (use FLEE first) and is subject to a
 * short cooldown so it cannot be used as a spammable escape/travel tool.
 */
public class RecallCommand extends RegistrableCommand {

    public RecallCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "recall";
    }

    @Override
    public String shortDescription() {
        return "Teleport back to town. No target, out-of-combat only, has a cooldown.";
    }

    @Override
    public String longDescription() {
        return """
               Usage: RECALL
                 Teleports you back to the starting/town room. You cannot recall while in
                 active combat \u2014 use FLEE first. Recall also has a short cooldown after each
                 use, so it cannot be used repeatedly as an escape button.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String token = SocketCommandParsing.splitInput(input)[0];
        if (!"RECALL".equals(token)) {
            return Optional.empty();
        }
        return Optional.of(new SocketCommandMatch(this, SocketCommandContext::recall));
    }
}

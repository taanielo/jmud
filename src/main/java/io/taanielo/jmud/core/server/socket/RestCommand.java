package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code REST} (or {@code SLEEP}) command, putting the player into
 * a resting state where HP, mana, and move are regenerated each game tick.
 *
 * <p>Resting is blocked while the player is in active combat. Any action command
 * or a mob hit cancels the resting state automatically.
 */
public class RestCommand extends RegistrableCommand {

    public RestCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "rest";
    }

    @Override
    public String shortDescription() {
        return "Sit down and rest to regenerate HP, mana, and move. Aliases: SLEEP";
    }

    @Override
    public String longDescription() {
        return """
               Usage: REST  |  SLEEP
                 Enters a resting state. While resting, HP, mana, and move regenerate
                 each game tick. Use WAKE or STAND to cancel resting voluntarily.
                 Resting is interrupted automatically when you move, attack, or are hit.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String token = SocketCommandParsing.splitInput(input)[0];
        if (!"REST".equals(token) && !"SLEEP".equals(token)) {
            return Optional.empty();
        }
        return Optional.of(new SocketCommandMatch(this, SocketCommandContext::startResting));
    }
}

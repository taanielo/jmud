package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code SHOOT} command: firing a ranged weapon at a mob in an adjacent room.
 *
 * <p>Syntax: {@code SHOOT <target> <direction>}. The direction identifies the adjacent room the
 * target occupies (e.g. {@code SHOOT goblin north}). Parsing of the target/direction split and all
 * validation happen in {@link SocketCommandContext#executeRangedAttack(String)}.
 */
public class RangedAttackCommand extends RegistrableCommand {
    public RangedAttackCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "shoot";
    }

    @Override
    public String shortDescription() {
        return "Fire a ranged weapon at a mob in an adjacent room.";
    }

    @Override
    public String longDescription() {
        return "Usage: SHOOT <target> <direction>\n"
             + "  Fires your equipped ranged weapon (bow, throwing knife) at the named mob in the\n"
             + "  adjacent room in the given direction. On a hit the mob closes in to melee.";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        String token = parts[0];
        if (!"SHOOT".equals(token)) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.executeRangedAttack(args)));
    }
}

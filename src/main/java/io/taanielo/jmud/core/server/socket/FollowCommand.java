package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code FOLLOW} command, letting a party member auto-follow another member's moves.
 *
 * <p>Forms:
 * <ul>
 *   <li>{@code FOLLOW}          — report who you are currently following</li>
 *   <li>{@code FOLLOW <name>}   — start following an online party member</li>
 *   <li>{@code FOLLOW OFF}      — stop following</li>
 *   <li>{@code UNFOLLOW}        — alias for {@code FOLLOW OFF}</li>
 * </ul>
 *
 * <p>Once following, the player is automatically walked one step behind the leader whenever the
 * leader successfully moves to an adjacent room, provided both are still partied and in the same
 * room. The relationship is cancelled (with a notice) if the follower is in combat, overburdened,
 * cannot take the same exit, leaves the party, or disconnects.
 */
public class FollowCommand extends RegistrableCommand {

    /**
     * Creates a {@code FollowCommand} and registers it with the given registry.
     *
     * @param registry the registry this command is part of
     */
    public FollowCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "follow";
    }

    @Override
    public String shortDescription() {
        return "Auto-follow a party member's movements. Use FOLLOW <name> or FOLLOW OFF.";
    }

    @Override
    public String longDescription() {
        return """
               Usage: FOLLOW [player|OFF]
                 FOLLOW              \u2014 show who you are currently following
                 FOLLOW <player>     \u2014 start following an online member of your party
                 FOLLOW OFF          \u2014 stop following (UNFOLLOW does the same)

               While following, you are moved one step behind the leader whenever they walk to an
               adjacent room. Following stops automatically if you are in combat, overburdened, cannot
               take the same exit, leave the party, or either of you disconnects.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        String token = parts[0];
        if ("UNFOLLOW".equals(token)) {
            return Optional.of(new SocketCommandMatch(this, context -> context.executeFollow("OFF")));
        }
        if ("FOLLOW".equals(token)) {
            String args = parts[1];
            return Optional.of(new SocketCommandMatch(this, context -> context.executeFollow(args)));
        }
        return Optional.empty();
    }
}

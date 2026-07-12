package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code ASSIST} command: joins the fight against the mob another player in the same
 * room is currently engaged with, without having to name the mob directly.
 */
public class AssistCommand extends RegistrableCommand {
    public AssistCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "assist";
    }

    @Override
    public String shortDescription() {
        return "Join the fight against the mob a party member is fighting.";
    }

    @Override
    public String longDescription() {
        return """
                Usage: ASSIST <player>
                  Engages you in combat against whatever mob the named player in your current room is
                  currently fighting, exactly as if you had typed KILL against that mob. Handy in a
                  party when several similar mobs are present and you cannot tell which one your
                  ally is fighting; pairs naturally with the {partyHp} prompt token. Assisting works
                  regardless of party membership. Fails if the named player is not here or is not
                  fighting anything.""";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        String token = parts[0];
        if (!"ASSIST".equals(token)) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.executeAssist(args)));
    }
}

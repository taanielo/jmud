package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code DUEL} command, challenging another player in the same room to a consensual
 * player-vs-player duel.
 */
public class DuelCommand extends RegistrableCommand {

    public DuelCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "duel";
    }

    @Override
    public String shortDescription() {
        return "Challenge another player in the room to a consensual duel.";
    }

    @Override
    public String longDescription() {
        return """
               Usage: DUEL <player>
                 Challenges a player in your room to a consensual duel. They must ACCEPT within 30
                 seconds to engage. Duels are fought to near death; the loser drops no items or gold
                 and the winner gains no experience.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"DUEL".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.initiateDuel(args)));
    }
}

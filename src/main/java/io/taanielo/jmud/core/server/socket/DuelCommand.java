package io.taanielo.jmud.core.server.socket;

import java.util.Locale;
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
                      DUEL WAGER <player> <gold>
                 Challenges a player in your room to a consensual duel. They must ACCEPT within 30
                 seconds to engage. The base DUEL <player> is always free and risk-free: the loser
                 drops no items or gold and the winner gains no experience.
                 DUEL WAGER <player> <gold> instead stakes a positive whole number of gold; you must
                 hold that much to challenge, and the accepting player must still hold it when they
                 ACCEPT. On resolution the wager transfers from the loser to the winner (clamped to
                 the loser's remaining gold). Forfeits, disconnects, and timeouts move no gold.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"DUEL".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        String[] argParts = args.split("\\s+", 2);
        if (argParts.length >= 1 && "WAGER".equals(argParts[0].toUpperCase(Locale.ROOT))) {
            String wagerArgs = argParts.length > 1 ? argParts[1] : "";
            return Optional.of(new SocketCommandMatch(this, context -> context.initiateWagerDuel(wagerArgs)));
        }
        return Optional.of(new SocketCommandMatch(this, context -> context.initiateDuel(args)));
    }
}

package io.taanielo.jmud.core.server.socket;

import java.util.Objects;
import java.util.Optional;

import io.taanielo.jmud.core.faction.ReputationService;
import io.taanielo.jmud.core.player.Player;

/**
 * Handles the {@code REPUTATION} command (aliased {@code REP}), listing the player's standing with
 * every faction they have a tracked (non-zero) reputation with, along with a derived
 * Hostile/Neutral/Friendly label.
 *
 * <p>Unlike {@code RANK}, this command performs no repository I/O: it reads the already-loaded
 * {@link Player#reputation()} from the command context and the faction definitions snapshotted in
 * memory by {@link ReputationService}. It is read-only and mutates no game state, so it is safe to run
 * straight off the current player without touching the tick loop (AGENTS.md §5).
 */
public class ReputationCommand extends RegistrableCommand {

    private final ReputationService reputationService;

    /**
     * Creates a {@code REPUTATION} command backed by the given reputation service.
     *
     * @param registry          the command registry to register with
     * @param reputationService resolves faction display names and standing labels; must not be null
     */
    public ReputationCommand(SocketCommandRegistry registry, ReputationService reputationService) {
        super(registry);
        this.reputationService = Objects.requireNonNull(reputationService, "ReputationService is required");
    }

    @Override
    public String name() {
        return "reputation";
    }

    @Override
    public String shortDescription() {
        return "Show your standing with every faction you have dealt with.";
    }

    @Override
    public String longDescription() {
        return """
               Usage: REPUTATION (or REP)
                 Lists every faction you have a tracked standing with, its numeric value,
                 and whether that faction regards you as Hostile, Neutral or Friendly.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String token = SocketCommandParsing.firstToken(input);
        if (!"REPUTATION".equals(token) && !"REP".equals(token)) {
            return Optional.empty();
        }
        return Optional.of(new SocketCommandMatch(this, this::handleReputation));
    }

    private void handleReputation(SocketCommandContext context) {
        if (!context.isAuthenticated() || context.getPlayer() == null) {
            context.writeLineWithPrompt("You must be logged in to view your reputation.");
            return;
        }
        Player player = context.getPlayer();
        for (String line : ReputationListing.format(player.reputation(), reputationService)) {
            context.writeLineSafe(line);
        }
        context.sendPrompt();
    }
}

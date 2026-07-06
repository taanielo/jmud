package io.taanielo.jmud.core.server.socket;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerRepository;

/**
 * Handles the {@code RANK} command, listing every persisted player's total
 * kill count ranked highest-to-lowest, server-wide.
 *
 * <p>Unlike {@code WHO}, which only lists players currently online, this
 * command reads every persisted player via {@link PlayerRepository#findAll()}
 * so offline players are included too. Reading every player file is a
 * blocking I/O operation, so this command must only ever run on a reader
 * thread (never on the tick thread) — it is read-only and performs no
 * game-state mutation.
 */
public class RankCommand extends RegistrableCommand {

    private final PlayerRepository playerRepository;

    /**
     * Creates a {@code RankCommand} backed by the given player repository.
     *
     * @param registry         the command registry to register with
     * @param playerRepository the repository used to enumerate all persisted players
     */
    public RankCommand(SocketCommandRegistry registry, PlayerRepository playerRepository) {
        super(registry);
        this.playerRepository = Objects.requireNonNull(playerRepository, "PlayerRepository is required");
    }

    @Override
    public String name() {
        return "rank";
    }

    @Override
    public String shortDescription() {
        return "List all players server-wide ranked by total kills.";
    }

    @Override
    public String longDescription() {
        return "Usage: RANK\n"
             + "  Displays every saved player's total kill count, ranked highest to lowest,\n"
             + "  including players who are not currently online.";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String token = SocketCommandParsing.firstToken(input);
        if (!"RANK".equals(token)) {
            return Optional.empty();
        }
        return Optional.of(new SocketCommandMatch(this, this::handleRank));
    }

    private void handleRank(SocketCommandContext context) {
        if (!context.isAuthenticated() || context.getPlayer() == null) {
            context.writeLineWithPrompt("You must be logged in to view the kill ranking.");
            return;
        }
        List<Player> players = playerRepository.findAll();
        for (String line : KillRankingListing.format(players)) {
            context.writeLineSafe(line);
        }
        context.sendPrompt();
    }
}

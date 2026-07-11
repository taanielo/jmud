package io.taanielo.jmud.core.server.socket;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerRepository;

/**
 * Handles the {@code RANK} command, listing every persisted player server-wide.
 *
 * <p>With no argument it ranks players by total kill count (PvE) highest-to-lowest.
 * The {@code RANK DUELS} subcommand instead ranks players by their persistent duel
 * record (PvP), listing only those with at least one recorded duel.
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
        return "Usage: RANK [DUELS]\n"
             + "  RANK        Displays every saved player's total kill count, ranked highest to\n"
             + "              lowest, including players who are not currently online.\n"
             + "  RANK DUELS  Displays every saved player's duel record (wins/losses/win rate),\n"
             + "              ranked by wins; only players with at least one duel are listed.";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"RANK".equals(parts[0])) {
            return Optional.empty();
        }
        String argument = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> handleRank(context, argument)));
    }

    private void handleRank(SocketCommandContext context, String argument) {
        if (!context.isAuthenticated() || context.getPlayer() == null) {
            context.writeLineWithPrompt("You must be logged in to view the ranking.");
            return;
        }
        List<Player> players = playerRepository.findAll();
        boolean duels = "DUELS".equals(argument.toUpperCase(Locale.ROOT));
        List<String> lines = duels
            ? DuelRankingListing.format(players)
            : KillRankingListing.format(players);
        for (String line : lines) {
            context.writeLineSafe(line);
        }
        context.sendPrompt();
    }
}

package io.taanielo.jmud.core.server.socket;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import io.taanielo.jmud.core.guild.Guild;
import io.taanielo.jmud.core.guild.GuildRepository;
import io.taanielo.jmud.core.guild.GuildRepositoryException;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerRepository;

/**
 * Handles the {@code RANK} command, listing every persisted player or guild server-wide.
 *
 * <p>With no argument it ranks players by total kill count (PvE) highest-to-lowest.
 * The {@code RANK DUELS} subcommand instead ranks players by their persistent duel
 * record (PvP), listing only those with at least one recorded duel. The
 * {@code RANK GUILDS} subcommand ranks every persisted guild by level (highest tier
 * first), tiebroken by lifetime deposited gold.
 *
 * <p>Unlike {@code WHO}, which only lists players currently online, this
 * command reads every persisted player via {@link PlayerRepository#findAll()}
 * (and every guild via {@link GuildRepository#loadAll()}) so offline players and
 * guilds are included too. Reading every record is a blocking I/O operation, so
 * this command must only ever run on a reader thread (never on the tick thread) —
 * it is read-only and performs no game-state mutation.
 */
public class RankCommand extends RegistrableCommand {

    private final PlayerRepository playerRepository;
    private final GuildRepository guildRepository;

    /**
     * Creates a {@code RankCommand} backed by the given repositories.
     *
     * @param registry         the command registry to register with
     * @param playerRepository the repository used to enumerate all persisted players
     * @param guildRepository  the repository used to enumerate all persisted guilds
     */
    public RankCommand(
        SocketCommandRegistry registry, PlayerRepository playerRepository, GuildRepository guildRepository) {
        super(registry);
        this.playerRepository = Objects.requireNonNull(playerRepository, "PlayerRepository is required");
        this.guildRepository = Objects.requireNonNull(guildRepository, "GuildRepository is required");
    }

    @Override
    public String name() {
        return "rank";
    }

    @Override
    public String shortDescription() {
        return "List all players (or guilds) server-wide ranked by kills, duels, or guild level.";
    }

    @Override
    public String longDescription() {
        return """
               Usage: RANK [DUELS|GUILDS]
                 RANK         Displays every saved player's total kill count, ranked highest to
                              lowest, including players who are not currently online.
                 RANK DUELS   Displays every saved player's duel record (wins/losses/win rate),
                              ranked by wins; only players with at least one duel are listed.
                 RANK GUILDS  Displays every guild server-wide, ranked by guild level (highest
                              first), tiebroken by lifetime deposited gold; shows each guild's
                              level, member count, and leader.\
               """;
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
        String mode = argument.toUpperCase(Locale.ROOT);
        List<String> lines = switch (mode) {
            case "DUELS" -> DuelRankingListing.format(playerRepository.findAll());
            case "GUILDS" -> GuildRankingListing.format(loadGuilds());
            default -> KillRankingListing.format(playerRepository.findAll());
        };
        for (String line : lines) {
            context.writeLineSafe(line);
        }
        context.sendPrompt();
    }

    private List<Guild> loadGuilds() {
        try {
            return guildRepository.loadAll();
        } catch (GuildRepositoryException e) {
            return List.of();
        }
    }
}

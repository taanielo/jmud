package io.taanielo.jmud.core.server.socket;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.player.Player;

/**
 * Pure, network-free helper that formats a global duel-record ranking for the
 * {@code RANK DUELS} command.
 *
 * <p>Kept free of any I/O so the ranking, ordering, and win-rate logic can be
 * unit tested in isolation. Only players with at least one recorded duel (a win
 * or a loss) are listed. Players are sorted by {@link Player#getDuelWins()}
 * descending, with ties broken by fewer losses and then by username for a
 * stable, deterministic order.
 */
public final class DuelRankingListing {

    private static final String HEADER = "Duel ranking:";
    private static final int DEFAULT_LIMIT = 20;

    private DuelRankingListing() {
    }

    /**
     * Formats the given players into a descending duel-win ranking, capped at
     * {@value #DEFAULT_LIMIT} entries.
     *
     * @param players the persisted players to rank, in any order
     * @return the lines to render, never empty
     */
    public static List<String> format(List<Player> players) {
        return format(players, DEFAULT_LIMIT);
    }

    /**
     * Formats the given players into a descending duel-win ranking, capped at
     * {@code limit} entries. Players with no recorded duels are omitted.
     *
     * @param players the persisted players to rank, in any order
     * @param limit   the maximum number of ranked entries to include
     * @return the lines to render, never empty
     */
    public static List<String> format(List<Player> players, int limit) {
        Objects.requireNonNull(players, "Players are required");
        List<Player> ranked = new ArrayList<>();
        for (Player player : players) {
            if (player.getDuelWins() > 0 || player.getDuelLosses() > 0) {
                ranked.add(player);
            }
        }
        ranked.sort(
            Comparator.comparingInt(Player::getDuelWins).reversed()
                .thenComparingInt(Player::getDuelLosses)
                .thenComparing(p -> p.getUsername().getValue()));

        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        int shown = Math.min(ranked.size(), Math.max(0, limit));
        for (int i = 0; i < shown; i++) {
            Player player = ranked.get(i);
            lines.add(String.format(
                "  %2d. %-15s %dW / %dL (%d%% win)",
                i + 1,
                player.getUsername().getValue(),
                player.getDuelWins(),
                player.getDuelLosses(),
                winPercent(player.getDuelWins(), player.getDuelLosses())));
        }
        if (ranked.size() > shown) {
            lines.add("  ... and " + (ranked.size() - shown) + " more.");
        }
        lines.add(footer(ranked.size()));
        return List.copyOf(lines);
    }

    /**
     * Computes the integer win percentage (0&ndash;100) from a win/loss record,
     * rounded to the nearest whole percent. A record with no duels yields 0.
     *
     * @param wins   the number of duels won; must be non-negative
     * @param losses the number of duels lost; must be non-negative
     * @return the win percentage, rounded to the nearest whole number
     */
    static int winPercent(int wins, int losses) {
        int total = wins + losses;
        if (total <= 0) {
            return 0;
        }
        return (int) Math.round(100.0 * wins / total);
    }

    /**
     * Builds the count footer line, pluralising "duelist" as appropriate.
     *
     * @param count the total number of ranked duelists
     * @return the footer line, e.g. {@code "3 duelists ranked."}
     */
    static String footer(int count) {
        String noun = count == 1 ? "duelist" : "duelists";
        return count + " " + noun + " ranked.";
    }
}

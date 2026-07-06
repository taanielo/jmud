package io.taanielo.jmud.core.server.socket;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.player.Player;

/**
 * Pure, network-free helper that formats a global kill-count ranking for the
 * {@code RANK} command.
 *
 * <p>Kept free of any I/O so the ranking, ordering, and pagination logic can be
 * unit tested in isolation. Players are sorted by {@link Player#getTotalKills()}
 * descending, with ties broken by username for a stable, deterministic order.
 */
public final class KillRankingListing {

    private static final String HEADER = "Kill ranking:";
    private static final int DEFAULT_LIMIT = 20;

    private KillRankingListing() {
    }

    /**
     * Formats the given players into a descending kill-count ranking, capped at
     * {@value #DEFAULT_LIMIT} entries.
     *
     * @param players the persisted players to rank, in any order
     * @return the lines to render, never empty
     */
    public static List<String> format(List<Player> players) {
        return format(players, DEFAULT_LIMIT);
    }

    /**
     * Formats the given players into a descending kill-count ranking, capped at
     * {@code limit} entries.
     *
     * @param players the persisted players to rank, in any order
     * @param limit   the maximum number of ranked entries to include
     * @return the lines to render, never empty
     */
    public static List<String> format(List<Player> players, int limit) {
        Objects.requireNonNull(players, "Players are required");
        List<Player> ranked = new ArrayList<>(players);
        ranked.sort(
            Comparator.comparingLong(Player::getTotalKills).reversed()
                .thenComparing(p -> p.getUsername().getValue()));

        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        int shown = Math.min(ranked.size(), Math.max(0, limit));
        for (int i = 0; i < shown; i++) {
            Player player = ranked.get(i);
            lines.add(String.format("  %2d. %-15s %d kills", i + 1, player.getUsername().getValue(), player.getTotalKills()));
        }
        if (ranked.size() > shown) {
            lines.add("  ... and " + (ranked.size() - shown) + " more.");
        }
        lines.add(footer(ranked.size()));
        return List.copyOf(lines);
    }

    /**
     * Builds the count footer line, pluralising "player" as appropriate.
     *
     * @param count the total number of ranked players
     * @return the footer line, e.g. {@code "3 players ranked."}
     */
    static String footer(int count) {
        String noun = count == 1 ? "player" : "players";
        return count + " " + noun + " ranked.";
    }
}

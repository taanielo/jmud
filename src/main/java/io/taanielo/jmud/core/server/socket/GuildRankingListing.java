package io.taanielo.jmud.core.server.socket;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.guild.Guild;

/**
 * Pure, network-free helper that formats a server-wide guild ranking for the
 * {@code RANK GUILDS} command.
 *
 * <p>Kept free of any I/O so the ranking, ordering, and formatting logic can be
 * unit tested in isolation. Guilds are sorted by {@link Guild#level() level}
 * (highest tier first), with ties broken by {@link Guild#lifetimeDepositedGold()}
 * lifetime deposited gold descending, and finally by guild name for a stable,
 * deterministic order. Each line shows the guild's rank, name, level, member
 * count, and leader's username.
 */
public final class GuildRankingListing {

    private static final String HEADER = "Guild ranking:";
    private static final String EMPTY = "No guilds have been founded yet.";
    private static final int DEFAULT_LIMIT = 20;

    private GuildRankingListing() {
    }

    /**
     * Formats the given guilds into a descending level ranking, capped at
     * {@value #DEFAULT_LIMIT} entries.
     *
     * @param guilds the persisted guilds to rank, in any order
     * @return the lines to render, never empty
     */
    public static List<String> format(List<Guild> guilds) {
        return format(guilds, DEFAULT_LIMIT);
    }

    /**
     * Formats the given guilds into a descending level ranking, capped at
     * {@code limit} entries. When no guilds exist a single friendly message is
     * returned instead of an empty table.
     *
     * @param guilds the persisted guilds to rank, in any order
     * @param limit  the maximum number of ranked entries to include
     * @return the lines to render, never empty
     */
    public static List<String> format(List<Guild> guilds, int limit) {
        Objects.requireNonNull(guilds, "Guilds are required");
        if (guilds.isEmpty()) {
            return List.of(EMPTY);
        }
        List<Guild> ranked = new ArrayList<>(guilds);
        ranked.sort(
            Comparator.comparingInt((Guild g) -> g.level().rank()).reversed()
                .thenComparing(Comparator.comparingInt(Guild::lifetimeDepositedGold).reversed())
                .thenComparing(Guild::name));

        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        int shown = Math.min(ranked.size(), Math.max(0, limit));
        for (int i = 0; i < shown; i++) {
            Guild guild = ranked.get(i);
            lines.add(String.format(
                "  %2d. %-24s L%d  %d members  led by %s",
                i + 1,
                guild.name(),
                guild.level().rank(),
                guild.memberCount(),
                guild.leaderId().getValue()));
        }
        if (ranked.size() > shown) {
            lines.add("  ... and " + (ranked.size() - shown) + " more.");
        }
        lines.add(footer(ranked.size()));
        return List.copyOf(lines);
    }

    /**
     * Builds the count footer line, pluralising "guild" as appropriate.
     *
     * @param count the total number of ranked guilds
     * @return the footer line, e.g. {@code "3 guilds ranked."}
     */
    static String footer(int count) {
        String noun = count == 1 ? "guild" : "guilds";
        return count + " " + noun + " ranked.";
    }
}

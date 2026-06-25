package io.taanielo.jmud.core.server.socket;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.authentication.Username;

/**
 * Pure, network-free helper that formats the roster of online players for the
 * {@code who} command.
 *
 * <p>Kept free of any I/O so the listing, count, and ordering logic can be unit
 * tested in isolation. The same rendered output is reused by every transport
 * (telnet and SSH) since both are driven by {@code SocketClient}.
 */
public final class WhoListing {

    private static final String HEADER = "Players online:";

    private WhoListing() {
    }

    /**
     * Formats the given online player names into display lines.
     *
     * <p>The output always starts with a header, lists each player name indented
     * on its own line, and ends with a footer carrying the total count. The input
     * order is preserved.
     *
     * @param onlineNames the authenticated, connected player names to list
     * @return the lines to render, never empty
     */
    public static List<String> format(List<Username> onlineNames) {
        Objects.requireNonNull(onlineNames, "Online names are required");
        List<String> lines = new ArrayList<>(onlineNames.size() + 2);
        lines.add(HEADER);
        for (Username name : onlineNames) {
            lines.add("  " + Objects.requireNonNull(name, "Online name is required").getValue());
        }
        lines.add(footer(onlineNames.size()));
        return List.copyOf(lines);
    }

    /**
     * Builds the count footer line, pluralising "player" as appropriate.
     *
     * @param count the number of online players
     * @return the footer line, e.g. {@code "3 players online."}
     */
    static String footer(int count) {
        String noun = count == 1 ? "player" : "players";
        return count + " " + noun + " online.";
    }
}

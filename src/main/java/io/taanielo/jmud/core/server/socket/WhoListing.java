package io.taanielo.jmud.core.server.socket;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

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
        return format(onlineNames, name -> "");
    }

    /**
     * Formats the given online player names into display lines, appending each player's guild tag.
     *
     * <p>Behaves like {@link #format(List)} but calls {@code tagResolver} for every name and appends
     * its return value (e.g. {@code " [Ironclad]"}) directly after the name. The resolver must return
     * an empty string for guildless players, never {@code null}.
     *
     * @param onlineNames the authenticated, connected player names to list
     * @param tagResolver resolves the guild-tag suffix for each name
     * @return the lines to render, never empty
     */
    public static List<String> format(List<Username> onlineNames, Function<Username, String> tagResolver) {
        return format(onlineNames, tagResolver, name -> "");
    }

    /**
     * Formats the given online player names into display lines, appending each player's guild tag
     * and active title suffix.
     *
     * <p>Behaves like {@link #format(List, Function)} but, after the guild tag, also appends the
     * result of {@code titleResolver} (e.g. {@code " the Centurion"}). Both resolvers must return an
     * empty string when there is nothing to show for a player, never {@code null}. The rendered order
     * is {@code name + guildTag + title}, so a fully-decorated line reads {@code "Sparky [Ironclad]
     * the Centurion"}.
     *
     * @param onlineNames   the authenticated, connected player names to list
     * @param tagResolver   resolves the guild-tag suffix for each name
     * @param titleResolver resolves the active-title suffix for each name
     * @return the lines to render, never empty
     */
    public static List<String> format(
            List<Username> onlineNames,
            Function<Username, String> tagResolver,
            Function<Username, String> titleResolver) {
        return format(onlineNames, tagResolver, titleResolver, name -> false);
    }

    /**
     * Formats the given online player names into display lines, appending each player's guild tag
     * and active title suffix and marking those on the viewer's friends list.
     *
     * <p>Behaves like {@link #format(List, Function, Function)} but replaces the two-space indent
     * with a {@code "* "} prefix for any name for which {@code isFriend} returns {@code true},
     * visually distinguishing the viewer's friends. A fully-decorated friend line reads
     * {@code "* Sparky [Ironclad] the Centurion"}.
     *
     * @param onlineNames   the authenticated, connected player names to list
     * @param tagResolver   resolves the guild-tag suffix for each name
     * @param titleResolver resolves the active-title suffix for each name
     * @param isFriend      tests whether each name is on the viewer's friends list
     * @return the lines to render, never empty
     */
    public static List<String> format(
            List<Username> onlineNames,
            Function<Username, String> tagResolver,
            Function<Username, String> titleResolver,
            Predicate<Username> isFriend) {
        return format(onlineNames, tagResolver, titleResolver, isFriend, name -> false);
    }

    /**
     * Formats the given online player names into display lines, appending each player's guild tag
     * and active title suffix, marking friends, and tagging away players with {@code [AFK]}.
     *
     * <p>Behaves like {@link #format(List, Function, Function, Predicate)} but appends {@code " [AFK]"}
     * after the title for any name for which {@code isAway} returns {@code true}. A fully-decorated
     * away friend line reads {@code "* Sparky [Ironclad] the Centurion [AFK]"}.
     *
     * @param onlineNames   the authenticated, connected player names to list
     * @param tagResolver   resolves the guild-tag suffix for each name
     * @param titleResolver resolves the active-title suffix for each name
     * @param isFriend      tests whether each name is on the viewer's friends list
     * @param isAway        tests whether each name is currently away from keyboard
     * @return the lines to render, never empty
     */
    public static List<String> format(
            List<Username> onlineNames,
            Function<Username, String> tagResolver,
            Function<Username, String> titleResolver,
            Predicate<Username> isFriend,
            Predicate<Username> isAway) {
        return format(onlineNames, tagResolver, titleResolver, isFriend, isAway, name -> "", name -> "");
    }

    /**
     * Formats the given online player names into fully-decorated display lines, including each
     * player's level/class and any looking-for-group tag (issue #510).
     *
     * <p>Behaves like {@link #format(List, Function, Function, Predicate, Predicate)} but inserts the
     * result of {@code levelClassResolver} (e.g. {@code " [12 Warrior]"}) immediately after the name
     * and appends the result of {@code lfgResolver} (e.g. {@code " [LFG]"} or
     * {@code " [LFG: tank for Catacombs]"}) at the very end. Both resolvers must return an empty
     * string when there is nothing to show for a player, never {@code null}. The rendered order is
     * {@code name + levelClass + guildTag + title + afk + lfg}, so a fully-decorated line reads
     * {@code "* Sparky [12 Warrior] [Ironclad] the Centurion [AFK] [LFG: tank]"}.
     *
     * @param onlineNames        the authenticated, connected player names to list
     * @param tagResolver        resolves the guild-tag suffix for each name
     * @param titleResolver      resolves the active-title suffix for each name
     * @param isFriend           tests whether each name is on the viewer's friends list
     * @param isAway             tests whether each name is currently away from keyboard
     * @param levelClassResolver resolves the level/class suffix for each name
     * @param lfgResolver        resolves the looking-for-group suffix for each name
     * @return the lines to render, never empty
     */
    public static List<String> format(
            List<Username> onlineNames,
            Function<Username, String> tagResolver,
            Function<Username, String> titleResolver,
            Predicate<Username> isFriend,
            Predicate<Username> isAway,
            Function<Username, String> levelClassResolver,
            Function<Username, String> lfgResolver) {
        return format(onlineNames, tagResolver, titleResolver, isFriend, isAway, levelClassResolver,
            lfgResolver, name -> "");
    }

    /**
     * Formats the given online player names into fully-decorated display lines, additionally appending
     * each player's marital-status suffix (issue #649).
     *
     * <p>Behaves like {@link #format(List, Function, Function, Predicate, Predicate, Function, Function)}
     * but appends the result of {@code marriedResolver} (e.g. {@code " (Married to Alice)"}) at the very
     * end of each line. The resolver must return an empty string when a player is unmarried, never
     * {@code null}. The rendered order is {@code name + levelClass + guildTag + title + afk + lfg +
     * married}.
     *
     * @param onlineNames        the authenticated, connected player names to list
     * @param tagResolver        resolves the guild-tag suffix for each name
     * @param titleResolver      resolves the active-title suffix for each name
     * @param isFriend           tests whether each name is on the viewer's friends list
     * @param isAway             tests whether each name is currently away from keyboard
     * @param levelClassResolver resolves the level/class suffix for each name
     * @param lfgResolver        resolves the looking-for-group suffix for each name
     * @param marriedResolver    resolves the marital-status suffix for each name
     * @return the lines to render, never empty
     */
    public static List<String> format(
            List<Username> onlineNames,
            Function<Username, String> tagResolver,
            Function<Username, String> titleResolver,
            Predicate<Username> isFriend,
            Predicate<Username> isAway,
            Function<Username, String> levelClassResolver,
            Function<Username, String> lfgResolver,
            Function<Username, String> marriedResolver) {
        Objects.requireNonNull(onlineNames, "Online names are required");
        Objects.requireNonNull(tagResolver, "Tag resolver is required");
        Objects.requireNonNull(titleResolver, "Title resolver is required");
        Objects.requireNonNull(isFriend, "Friend predicate is required");
        Objects.requireNonNull(isAway, "Away predicate is required");
        Objects.requireNonNull(levelClassResolver, "Level/class resolver is required");
        Objects.requireNonNull(lfgResolver, "LFG resolver is required");
        Objects.requireNonNull(marriedResolver, "Married resolver is required");
        List<String> lines = new ArrayList<>(onlineNames.size() + 2);
        lines.add(HEADER);
        for (Username name : onlineNames) {
            Username resolved = Objects.requireNonNull(name, "Online name is required");
            String levelClass = Objects.requireNonNullElse(levelClassResolver.apply(resolved), "");
            String tag = Objects.requireNonNullElse(tagResolver.apply(resolved), "");
            String title = Objects.requireNonNullElse(titleResolver.apply(resolved), "");
            String prefix = isFriend.test(resolved) ? "* " : "  ";
            String afk = isAway.test(resolved) ? " [AFK]" : "";
            String lfg = Objects.requireNonNullElse(lfgResolver.apply(resolved), "");
            String married = Objects.requireNonNullElse(marriedResolver.apply(resolved), "");
            lines.add(prefix + resolved.getValue() + levelClass + tag + title + afk + lfg + married);
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

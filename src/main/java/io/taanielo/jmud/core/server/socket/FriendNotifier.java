package io.taanielo.jmud.core.server.socket;

import java.util.ArrayList;
import java.util.List;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerFriendList;

/**
 * Pure, network-free helpers for friend arrival/departure notices (issue #508).
 *
 * <p>Encapsulates the two decisions worth testing in isolation: how a login/logout notice is worded
 * for the subject player, and which of the currently-online players should receive it (the ones who
 * have the subject on their one-directional {@code FRIEND} list, never the subject themselves).
 * Keeping this logic here — free of session mutation, message delivery, and I/O — lets it be unit
 * tested without a running {@code GameContext} (AGENTS.md &sect;10). Actual delivery of the resulting
 * lines is done by {@link SocketCommandContextImpl} through the {@code MessageBroadcaster}
 * (AGENTS.md &sect;3.3).
 */
final class FriendNotifier {

    private FriendNotifier() {
    }

    /**
     * A minimal view of one currently-online player relevant to friend notification.
     *
     * @param username the online player's username
     * @param friends  that player's friends list
     */
    record OnlinePlayer(Username username, PlayerFriendList friends) {
    }

    /**
     * Formats the notice shown to friends when {@code subject} enters the game.
     *
     * @param subject the player who has logged in
     * @return {@code "Your friend <Name> has entered the game."}
     */
    static String loginMessage(Player subject) {
        return "Your friend " + subject.getUsername().getValue() + " has entered the game.";
    }

    /**
     * Formats the notice shown to friends when {@code subject} leaves the game.
     *
     * @param subject the player who has logged out
     * @return {@code "Your friend <Name> has left the game."}
     */
    static String logoutMessage(Player subject) {
        return "Your friend " + subject.getUsername().getValue() + " has left the game.";
    }

    /**
     * Selects, from the currently-online players, those who should be notified about {@code subject}:
     * every online player, other than {@code subject} itself, whose friends list contains
     * {@code subject}. Because the {@code FRIEND} relationship is one-directional, only players who
     * friended {@code subject} are returned, never the reverse.
     *
     * @param subject the player whose login/logout is being announced
     * @param online  the currently-online players and their friends lists
     * @return the usernames to notify, in the given iteration order
     */
    static List<Username> recipients(Username subject, List<OnlinePlayer> online) {
        List<Username> recipients = new ArrayList<>();
        String subjectName = subject.getValue();
        for (OnlinePlayer candidate : online) {
            if (candidate.username().equals(subject)) {
                continue;
            }
            if (candidate.friends().has(subjectName)) {
                recipients.add(candidate.username());
            }
        }
        return recipients;
    }
}

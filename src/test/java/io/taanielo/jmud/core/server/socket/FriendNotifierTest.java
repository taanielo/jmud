package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerFriendList;

/**
 * Unit tests for {@link FriendNotifier}, the pure recipient-selection and message-wording logic
 * behind friend arrival/departure notices (issue #508).
 */
class FriendNotifierTest {

    private static Player player(String name) {
        return Player.of(User.of(Username.of(name), Password.hash("secret")), "%h/%H hp>");
    }

    private static FriendNotifier.OnlinePlayer online(String name, String... friends) {
        return new FriendNotifier.OnlinePlayer(Username.of(name), new PlayerFriendList(List.of(friends)));
    }

    // ── Message wording ────────────────────────────────────────────────

    @Test
    void loginMessageNamesTheSubject() {
        assertEquals("Your friend Riona has entered the game.", FriendNotifier.loginMessage(player("Riona")));
    }

    @Test
    void logoutMessageNamesTheSubject() {
        assertEquals("Your friend Riona has left the game.", FriendNotifier.logoutMessage(player("Riona")));
    }

    // ── Recipient selection ────────────────────────────────────────────

    @Test
    void notifiesEveryOnlinePlayerWhoFriendedTheSubject() {
        Username c = Username.of("Riona");
        List<FriendNotifier.OnlinePlayer> online = List.of(
            online("Alice", "Riona"),
            online("Bob", "Riona"),
            online("Riona"));

        List<Username> recipients = FriendNotifier.recipients(c, online);

        assertEquals(List.of(Username.of("Alice"), Username.of("Bob")), recipients);
    }

    @Test
    void doesNotNotifyPlayersWhoHaveNotFriendedTheSubject() {
        Username c = Username.of("Riona");
        List<FriendNotifier.OnlinePlayer> online = List.of(
            online("Alice", "Riona"),
            online("Dana", "Someone", "Else"));

        List<Username> recipients = FriendNotifier.recipients(c, online);

        assertTrue(recipients.contains(Username.of("Alice")));
        assertFalse(recipients.contains(Username.of("Dana")), "Dana never friended Riona");
    }

    @Test
    void neverNotifiesTheSubjectAboutThemselves() {
        Username c = Username.of("Riona");
        // Even if the subject somehow lists themselves, they are excluded from their own notice.
        List<FriendNotifier.OnlinePlayer> online = List.of(online("Riona", "Riona"));

        assertTrue(FriendNotifier.recipients(c, online).isEmpty());
    }

    @Test
    void friendMatchingIsCaseInsensitive() {
        Username c = Username.of("Riona");
        List<FriendNotifier.OnlinePlayer> online = List.of(online("Alice", "riona"));

        assertEquals(List.of(Username.of("Alice")), FriendNotifier.recipients(c, online));
    }

    @Test
    void relationshipIsOneDirectional() {
        // Riona friended Alice, but Alice did not friend Riona: Alice is not notified about Riona.
        Username riona = Username.of("Riona");
        List<FriendNotifier.OnlinePlayer> online = List.of(online("Alice"));

        assertTrue(FriendNotifier.recipients(riona, online).isEmpty());
    }

    @Test
    void noOnlinePlayersMeansNoRecipients() {
        assertTrue(FriendNotifier.recipients(Username.of("Riona"), List.of()).isEmpty());
    }
}

package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.world.Direction;

/**
 * Unit tests for {@link EmoteCommand}.
 */
class EmoteCommandTest {

    // --- token matching ---

    @Test
    void matchesEmoteToken() {
        EmoteCommand cmd = new EmoteCommand(new SocketCommandRegistry());
        assertTrue(cmd.match("EMOTE waves at the goblin.").isPresent());
        assertTrue(cmd.match("emote waves at the goblin.").isPresent());
    }

    @Test
    void matchesMeAlias() {
        EmoteCommand cmd = new EmoteCommand(new SocketCommandRegistry());
        assertTrue(cmd.match("ME bows deeply.").isPresent());
        assertTrue(cmd.match("me bows deeply.").isPresent());
    }

    @Test
    void doesNotMatchOtherTokens() {
        EmoteCommand cmd = new EmoteCommand(new SocketCommandRegistry());
        assertFalse(cmd.match("SAY hello").isPresent());
        assertFalse(cmd.match("GOSSIP hello").isPresent());
        assertFalse(cmd.match("LOOK").isPresent());
        assertFalse(cmd.match("").isPresent());
    }

    // --- room-broadcast behaviour ---

    @Test
    void actingPlayerSeesYouEmoteLine() {
        CapturingContext context = new CapturingContext("Taaniel");

        EmoteCommand cmd = new EmoteCommand(new SocketCommandRegistry());
        cmd.match("EMOTE waves at the goblin.").get().execute(context);

        assertTrue(context.lines.stream().anyMatch(l -> l.equals("You emote: Taaniel waves at the goblin.")),
                "Acting player should see 'You emote: <Name> <text>'");
    }

    @Test
    void roomReceivesNamePrependedAction() {
        CapturingContext context = new CapturingContext("Taaniel");

        EmoteCommand cmd = new EmoteCommand(new SocketCommandRegistry());
        cmd.match("EMOTE waves at the goblin.").get().execute(context);

        assertEquals("Taaniel waves at the goblin.", context.lastRoomMessage,
                "Room occupants should see '<Name> <text>'");
    }

    @Test
    void meAliasAlsoBroadcastsToRoom() {
        CapturingContext context = new CapturingContext("Taaniel");

        EmoteCommand cmd = new EmoteCommand(new SocketCommandRegistry());
        cmd.match("ME bows deeply.").get().execute(context);

        assertEquals("Taaniel bows deeply.", context.lastRoomMessage,
                "ME alias should also broadcast to the room");
        assertTrue(context.lines.stream().anyMatch(l -> l.equals("You emote: Taaniel bows deeply.")),
                "ME alias should show 'You emote:' to the acting player");
    }

    @Test
    void playersOutsideRoomReceiveNothing() {
        // sendToRoom is the sole broadcast mechanism; players outside are not
        // reachable via that call. We verify only the room message is set and
        // no global broadcast (gossip) was triggered.
        CapturingContext context = new CapturingContext("Taaniel");

        EmoteCommand cmd = new EmoteCommand(new SocketCommandRegistry());
        cmd.match("EMOTE waves.").get().execute(context);

        assertNull(context.gossipSender, "Emote must NOT trigger a gossip broadcast");
    }

    // --- blank / empty text ---

    @Test
    void blankEmoteShowsUsageHint() {
        CapturingContext context = new CapturingContext("Taaniel");

        EmoteCommand cmd = new EmoteCommand(new SocketCommandRegistry());
        cmd.match("EMOTE").get().execute(context);

        assertEquals("Emote what?", context.promptMessage,
                "Blank EMOTE should print 'Emote what?'");
    }

    @Test
    void whitespaceOnlyEmoteShowsUsageHint() {
        CapturingContext context = new CapturingContext("Taaniel");

        EmoteCommand cmd = new EmoteCommand(new SocketCommandRegistry());
        cmd.match("EMOTE    ").get().execute(context);

        assertEquals("Emote what?", context.promptMessage,
                "Whitespace-only EMOTE should print 'Emote what?'");
    }

    // --- help metadata ---

    @Test
    void shortDescriptionMentionsMeAlias() {
        EmoteCommand cmd = new EmoteCommand(new SocketCommandRegistry());
        assertTrue(cmd.shortDescription().contains("ME"),
                "shortDescription should mention ME alias");
    }

    @Test
    void longDescriptionContainsUsageAndAliases() {
        EmoteCommand cmd = new EmoteCommand(new SocketCommandRegistry());
        String desc = cmd.longDescription();
        assertTrue(desc.contains("EMOTE"), "longDescription should mention EMOTE");
        assertTrue(desc.contains("ME"), "longDescription should mention ME alias");
        assertTrue(desc.contains("Usage"), "longDescription should contain usage instructions");
    }

    // --- helpers ---

    private static Player stubPlayer(String name) {
        User user = User.of(Username.of(name), Password.hash("secret"));
        return Player.of(user, "%h/%H hp>");
    }

    private static class CapturingContext implements SocketCommandContext {
        final List<String> lines = new ArrayList<>();
        String promptMessage = "";
        String lastRoomMessage = null;
        String gossipSender = null;
        private final Player player;

        CapturingContext(String playerName) {
            this.player = stubPlayer(playerName);
        }

        @Override public void gossip(String senderName, String message) { this.gossipSender = senderName; }
        @Override public boolean isAuthenticated() { return true; }
        @Override public Player getPlayer() { return player; }
        @Override public List<Client> clients() { return List.of(); }
        @Override public List<Username> onlinePlayerNames() { return List.of(); }
        @Override public void sendLook() {}
        @Override public void sendLookAt(String t) {}
        @Override public void sendMove(Direction d) {}
        @Override public void useAbility(String a) {}
        @Override public void updateAnsi(String a) {}
        @Override public void writeLineWithPrompt(String m) { promptMessage = m; }
        @Override public void writeLineSafe(String m) { lines.add(m); }
        @Override public void sendPrompt() {}
        @Override public void sendToUsername(Username u, String m) {}
        @Override public void sendToRoom(Player s, Player t, String m) {}
        @Override public void sendToRoom(Player s, String m) { lastRoomMessage = m; }
        @Override public Optional<Player> resolveTarget(Player s, String i) { return Optional.empty(); }
        @Override public void executeAttack(String a) {}
        @Override public void getItem(String a) {}
        @Override public void dropItem(String a) {}
        @Override public void quaffItem(String a) {}
        @Override public void equipItem(String a) {}
        @Override public void unequipItem(String a) {}
        @Override public void killMob(String a) {}
        @Override public void fleeCombat() {}
        @Override public void sendInventory() {}
        @Override public void sendEquipment() {}
        @Override public void sendMessage(io.taanielo.jmud.core.messaging.Message m) {}
        @Override public void close() {}
        @Override public void run() {}
    }
}

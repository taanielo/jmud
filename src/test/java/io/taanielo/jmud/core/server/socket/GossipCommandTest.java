package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.world.Direction;

/**
 * Unit tests for {@link GossipCommand}.
 */
class GossipCommandTest {

    // --- token matching ---

    @Test
    void matchesGossipToken() {
        GossipCommand cmd = new GossipCommand(new SocketCommandRegistry());
        assertTrue(cmd.match("GOSSIP hello world").isPresent());
        assertTrue(cmd.match("gossip hello world").isPresent());
    }

    @Test
    void matchesGosAlias() {
        GossipCommand cmd = new GossipCommand(new SocketCommandRegistry());
        assertTrue(cmd.match("GOS hello").isPresent());
        assertTrue(cmd.match("gos hello").isPresent());
    }

    @Test
    void matchesGAlias() {
        GossipCommand cmd = new GossipCommand(new SocketCommandRegistry());
        assertTrue(cmd.match("G hello").isPresent());
        assertTrue(cmd.match("g hello").isPresent());
    }

    @Test
    void doesNotMatchOtherTokens() {
        GossipCommand cmd = new GossipCommand(new SocketCommandRegistry());
        assertFalse(cmd.match("SAY hello").isPresent());
        assertFalse(cmd.match("TELL someone hello").isPresent());
        assertFalse(cmd.match("WHO").isPresent());
        assertFalse(cmd.match("").isPresent());
    }

    // --- delivery ---

    @Test
    void senderSeesYouGossipLine() {
        CapturingContext context = new CapturingContext("Alice");

        GossipCommand cmd = new GossipCommand(new SocketCommandRegistry());
        cmd.match("GOSSIP hello world").get().execute(context);

        assertTrue(context.lines.stream().anyMatch(l -> l.equals("You gossip: hello world")),
                "Sender should see 'You gossip: <message>'");
    }

    @Test
    void gossipMethodCalledWithSenderNameAndMessage() {
        CapturingContext context = new CapturingContext("Alice");

        GossipCommand cmd = new GossipCommand(new SocketCommandRegistry());
        cmd.match("GOSSIP hello world").get().execute(context);

        assertEquals("Alice", context.gossipSender, "gossip() should receive the sender's name");
        assertEquals("hello world", context.gossipMessage, "gossip() should receive the trimmed message");
    }

    @Test
    void emptyMessageReturnsError() {
        CapturingContext context = new CapturingContext("Alice");

        GossipCommand cmd = new GossipCommand(new SocketCommandRegistry());
        cmd.match("GOSSIP").get().execute(context);

        assertFalse(context.promptMessage.isBlank(), "Error should be written via writeLineWithPrompt");
    }

    @Test
    void blankMessageReturnsError() {
        CapturingContext context = new CapturingContext("Alice");

        GossipCommand cmd = new GossipCommand(new SocketCommandRegistry());
        cmd.match("GOSSIP   ").get().execute(context);

        assertFalse(context.promptMessage.isBlank(), "Error should be written via writeLineWithPrompt");
    }

    @Test
    void shortDescriptionMentionsAliases() {
        GossipCommand cmd = new GossipCommand(new SocketCommandRegistry());
        String desc = cmd.shortDescription();
        assertTrue(desc.contains("GOS"), "shortDescription should mention GOS alias");
        assertTrue(desc.contains("G"), "shortDescription should mention G alias");
    }

    @Test
    void longDescriptionContainsUsage() {
        GossipCommand cmd = new GossipCommand(new SocketCommandRegistry());
        String desc = cmd.longDescription();
        assertTrue(desc.contains("GOSSIP"), "longDescription should mention GOSSIP");
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
        String gossipSender = null;
        String gossipMessage = null;
        final Map<String, String> sentToUsername = new HashMap<>();
        private final Player player;

        CapturingContext(String playerName) {
            this.player = stubPlayer(playerName);
        }

        @Override public void gossip(String senderName, String message) {
            this.gossipSender = senderName;
            this.gossipMessage = message;
        }

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
        @Override public void sendToUsername(Username u, String m) { sentToUsername.put(u.getValue().toLowerCase(), m); }
        @Override public void sendToRoom(Player s, Player t, String m) {}
        @Override public void sendToRoom(Player s, String m) {}
        @Override public Optional<Player> resolveTarget(Player s, String i) { return Optional.empty(); }
        @Override public void executeAttack(String a) {}
        @Override public void getItem(String a) {}
        @Override public void dropItem(String a) {}
        @Override public void quaffItem(String a) {}
        @Override public void readItem(String a) {}
        @Override public void writeItem(String a) {}
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

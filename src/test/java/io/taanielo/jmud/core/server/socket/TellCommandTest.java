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
 * Unit tests for {@link TellCommand}.
 */
class TellCommandTest {

    // --- token matching ---

    @Test
    void matchesTellToken() {
        TellCommand cmd = new TellCommand(new SocketCommandRegistry());
        assertTrue(cmd.match("TELL taaniel hello").isPresent());
        assertTrue(cmd.match("tell taaniel hello").isPresent());
    }

    @Test
    void matchesTAlias() {
        TellCommand cmd = new TellCommand(new SocketCommandRegistry());
        assertTrue(cmd.match("T taaniel hello").isPresent());
        assertTrue(cmd.match("t taaniel hello").isPresent());
    }

    @Test
    void doesNotMatchOtherTokens() {
        TellCommand cmd = new TellCommand(new SocketCommandRegistry());
        assertFalse(cmd.match("SAY hello").isPresent());
        assertFalse(cmd.match("WHO").isPresent());
        assertFalse(cmd.match("").isPresent());
    }

    // --- delivery ---

    @Test
    void deliversTellToRecipient() {
        CapturingContext context = new CapturingContext("Sender");
        context.addOnlinePlayer("Recipient");

        TellCommand cmd = new TellCommand(new SocketCommandRegistry());
        cmd.match("TELL Recipient hello there").get().execute(context);

        assertEquals("Sender tells you: hello there",
                context.sentToUsername.get("recipient"),
                "Recipient should receive the formatted tell message");
    }

    @Test
    void senderSeesConfirmation() {
        CapturingContext context = new CapturingContext("Sender");
        context.addOnlinePlayer("Recipient");

        TellCommand cmd = new TellCommand(new SocketCommandRegistry());
        cmd.match("TELL Recipient hello there").get().execute(context);

        assertTrue(context.lines.stream().anyMatch(l -> l.equals("You tell Recipient: hello there")),
                "Sender should see confirmation line");
    }

    @Test
    void playerNotOnlineReturnsError() {
        CapturingContext context = new CapturingContext("Sender");
        // No players added — target is offline.

        TellCommand cmd = new TellCommand(new SocketCommandRegistry());
        cmd.match("TELL Ghost hello").get().execute(context);

        assertTrue(context.promptMessage.contains("Ghost") && context.promptMessage.contains("not online"),
                "Error message should name the missing player");
    }

    @Test
    void missingMessageReturnsUsageError() {
        CapturingContext context = new CapturingContext("Sender");
        context.addOnlinePlayer("Recipient");

        TellCommand cmd = new TellCommand(new SocketCommandRegistry());
        cmd.match("TELL Recipient").get().execute(context);

        assertFalse(context.promptMessage.isBlank(), "Usage error should be written via writeLineWithPrompt");
        assertTrue(context.promptMessage.contains("Usage"), "Error should mention usage");
    }

    @Test
    void missingAllArgsReturnsUsageError() {
        CapturingContext context = new CapturingContext("Sender");

        TellCommand cmd = new TellCommand(new SocketCommandRegistry());
        cmd.match("TELL").get().execute(context);

        assertFalse(context.promptMessage.isBlank(), "Usage error should be written via writeLineWithPrompt");
        assertTrue(context.promptMessage.contains("Usage"), "Error should mention usage");
    }

    @Test
    void targetLookupIsCaseInsensitive() {
        CapturingContext context = new CapturingContext("Sender");
        context.addOnlinePlayer("TAANIEL");

        TellCommand cmd = new TellCommand(new SocketCommandRegistry());
        cmd.match("TELL taaniel hi").get().execute(context);

        assertFalse(context.sentToUsername.isEmpty(),
                "Tell should be delivered even when case differs");
    }

    // --- helpers ---

    private static Player stubPlayer(String name) {
        User user = User.of(Username.of(name), Password.hash("secret"));
        return Player.of(user, "%h/%H hp>");
    }

    private static class CapturingContext implements SocketCommandContext {
        final List<String> lines = new ArrayList<>();
        String promptMessage = "";
        final Map<String, String> sentToUsername = new HashMap<>();
        private final List<Username> onlinePlayers = new ArrayList<>();
        private final Player player;

        CapturingContext(String senderName) {
            this.player = stubPlayer(senderName);
        }

        void addOnlinePlayer(String name) {
            onlinePlayers.add(Username.of(name));
        }

        @Override public boolean isAuthenticated() { return true; }
        @Override public Player getPlayer() { return player; }
        @Override public List<Client> clients() { return List.of(); }
        @Override public List<Username> onlinePlayerNames() { return List.copyOf(onlinePlayers); }
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

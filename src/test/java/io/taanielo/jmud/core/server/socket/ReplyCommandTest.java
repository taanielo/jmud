package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.messaging.TellService;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerIgnoreList;
import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.world.Direction;

/**
 * Unit tests for {@link ReplyCommand}.
 */
class ReplyCommandTest {

    // --- token matching ---

    @Test
    void matchesReplyToken() {
        ReplyCommand cmd = new ReplyCommand(new SocketCommandRegistry(), new TellService());
        assertTrue(cmd.match("REPLY hello").isPresent());
        assertTrue(cmd.match("reply hello").isPresent());
    }

    @Test
    void matchesRAlias() {
        ReplyCommand cmd = new ReplyCommand(new SocketCommandRegistry(), new TellService());
        assertTrue(cmd.match("R hello").isPresent());
        assertTrue(cmd.match("r hello").isPresent());
    }

    @Test
    void doesNotMatchOtherTokens() {
        ReplyCommand cmd = new ReplyCommand(new SocketCommandRegistry(), new TellService());
        assertFalse(cmd.match("SAY hello").isPresent());
        assertFalse(cmd.match("WHO").isPresent());
        assertFalse(cmd.match("").isPresent());
    }

    // --- reply after a tell ---

    @Test
    void repliesToLastTellSender() {
        TellService tellService = new TellService();
        CapturingContext context = new CapturingContext("Sender");
        context.addOnlinePlayer("Recipient");

        // Recipient told Sender earlier this session.
        tellService.recordReceivedTell(Username.of("Sender"), Username.of("Recipient"));

        ReplyCommand cmd = new ReplyCommand(new SocketCommandRegistry(), tellService);
        cmd.match("REPLY hi back").get().execute(context);

        assertEquals("Sender tells you: hi back",
                context.sentToUsername.get("recipient"),
                "Reply should be delivered to the last tell sender in tell format");
        assertTrue(context.lines.stream().anyMatch(l -> l.equals("You tell Recipient: hi back")),
                "Sender should see the tell-style confirmation");
    }

    // --- reply after a whisper (same map, recorded on receipt) ---

    @Test
    void repliesToLastWhisperSender() {
        TellService tellService = new TellService();
        CapturingContext context = new CapturingContext("Sender");
        context.addOnlinePlayer("Whisperer");

        // A whisper received earlier records the whisperer as the reply target.
        tellService.recordReceivedTell(Username.of("Sender"), Username.of("Whisperer"));

        ReplyCommand cmd = new ReplyCommand(new SocketCommandRegistry(), tellService);
        cmd.match("REPLY over here").get().execute(context);

        assertEquals("Sender tells you: over here",
                context.sentToUsername.get("whisperer"),
                "Reply should target the last whisper sender");
    }

    // --- reply with no prior message ---

    @Test
    void replyWithNoPriorSenderExplains() {
        TellService tellService = new TellService();
        CapturingContext context = new CapturingContext("Sender");

        ReplyCommand cmd = new ReplyCommand(new SocketCommandRegistry(), tellService);
        cmd.match("REPLY hello").get().execute(context);

        assertTrue(context.sentToUsername.isEmpty(), "Nothing should be delivered with no reply target");
        assertTrue(context.promptMessage.contains("no one to reply to"),
                "Player should be told there is no one to reply to");
    }

    @Test
    void missingMessageReturnsUsageError() {
        TellService tellService = new TellService();
        CapturingContext context = new CapturingContext("Sender");
        tellService.recordReceivedTell(Username.of("Sender"), Username.of("Recipient"));

        ReplyCommand cmd = new ReplyCommand(new SocketCommandRegistry(), tellService);
        cmd.match("REPLY").get().execute(context);

        assertTrue(context.promptMessage.contains("Usage"), "Empty reply should show usage");
    }

    // --- reply to a now-offline sender ---

    @Test
    void replyToOfflineSenderReturnsClearError() {
        TellService tellService = new TellService();
        CapturingContext context = new CapturingContext("Sender");
        // Recipient told Sender, then disconnected: never added as an online player.
        tellService.recordReceivedTell(Username.of("Sender"), Username.of("Recipient"));

        ReplyCommand cmd = new ReplyCommand(new SocketCommandRegistry(), tellService);
        cmd.match("REPLY are you there").get().execute(context);

        assertTrue(context.sentToUsername.isEmpty(), "Nothing should be delivered to an offline target");
        assertTrue(context.promptMessage.contains("Recipient") && context.promptMessage.contains("no longer online"),
                "Player should be told the target went offline");
    }

    // --- reply respecting ignore lists ---

    @Test
    void replyFromIgnoredSenderIsSilentlyDropped() {
        TellService tellService = new TellService();
        CapturingContext context = new CapturingContext("Sender");
        context.addOnlinePlayer("Recipient", "Sender");
        tellService.recordReceivedTell(Username.of("Sender"), Username.of("Recipient"));

        ReplyCommand cmd = new ReplyCommand(new SocketCommandRegistry(), tellService);
        cmd.match("REPLY hi back").get().execute(context);

        assertTrue(context.sentToUsername.isEmpty(),
                "Recipient ignoring the sender should receive nothing");
        assertTrue(context.lines.stream().anyMatch(l -> l.equals("You tell Recipient: hi back")),
                "Sender should still see normal confirmation when ignored");
    }

    // --- receipt updates the reply pointer ---

    @Test
    void receivingTellUpdatesReplyPointer() {
        TellService tellService = new TellService();
        CapturingContext senderCtx = new CapturingContext("Bob");
        senderCtx.addOnlinePlayer("Alice");

        // Bob's reply pointer starts empty; simulate Alice telling Bob via TellCommand.
        TellCommand tell = new TellCommand(new SocketCommandRegistry(), tellService);
        CapturingContext aliceCtx = new CapturingContext("Alice");
        aliceCtx.addOnlinePlayer("Bob");
        tell.match("TELL Bob hi").get().execute(aliceCtx);

        assertEquals(Optional.of(Username.of("Alice")), tellService.lastSender(Username.of("Bob")),
                "Receiving a tell should record the sender as Bob's reply target");
    }

    // --- helpers ---

    private static Player stubPlayer(String name) {
        User user = User.of(Username.of(name), Password.hash("secret"));
        return Player.of(user, "%h/%H hp>");
    }

    private static Player stubPlayerIgnoring(String name, String... ignored) {
        return stubPlayer(name).withIgnoreList(new PlayerIgnoreList(List.of(ignored)));
    }

    private static final class CapturingContext implements SocketCommandContext {
        final List<String> lines = new ArrayList<>();
        String promptMessage = "";
        final Map<String, String> sentToUsername = new HashMap<>();
        private final List<Username> onlinePlayers = new ArrayList<>();
        private final Map<Username, Player> onlinePlayerObjects = new HashMap<>();
        private final Player player;

        CapturingContext(String senderName) {
            this.player = stubPlayer(senderName);
        }

        void addOnlinePlayer(String name) {
            onlinePlayers.add(Username.of(name));
            onlinePlayerObjects.put(Username.of(name), stubPlayer(name));
        }

        void addOnlinePlayer(String name, String... ignored) {
            onlinePlayers.add(Username.of(name));
            onlinePlayerObjects.put(Username.of(name), stubPlayerIgnoring(name, ignored));
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
        @Override public Player getOnlinePlayer(Username u) { return onlinePlayerObjects.get(u); }
        @Override public void sendToUsername(Username u, String m) { sentToUsername.put(u.getValue().toLowerCase(Locale.ROOT), m); }
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

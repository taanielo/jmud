package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.world.Direction;

/**
 * Unit tests for {@link PutCommand}, {@link GetFromCommand} and the {@link GetCommand} routing that
 * separates {@code GET <item>} (floor pickup) from {@code GET <item> FROM <container>}.
 */
class ContainerCommandTest {

    // ── PUT ──────────────────────────────────────────────────────────────

    @Test
    void putMatchesIntoSeparator() {
        PutCommand cmd = new PutCommand(new SocketCommandRegistry());
        CapturingContext context = new CapturingContext();

        cmd.match("PUT sword into bag").orElseThrow().execute(context);

        assertEquals("sword", context.putItem);
        assertEquals("bag", context.putContainer);
    }

    @Test
    void putMatchesInSeparator() {
        PutCommand cmd = new PutCommand(new SocketCommandRegistry());
        CapturingContext context = new CapturingContext();

        cmd.match("put a rusty key in a leather bag").orElseThrow().execute(context);

        assertEquals("a rusty key", context.putItem);
        assertEquals("a leather bag", context.putContainer);
    }

    @Test
    void putWithoutSeparatorShowsUsage() {
        PutCommand cmd = new PutCommand(new SocketCommandRegistry());
        CapturingContext context = new CapturingContext();

        cmd.match("PUT sword").orElseThrow().execute(context);

        assertTrue(context.promptMessage.contains("Usage"));
        assertNull(context.putItem);
    }

    @Test
    void putDoesNotMatchOtherTokens() {
        PutCommand cmd = new PutCommand(new SocketCommandRegistry());
        assertFalse(cmd.match("GET sword").isPresent());
        assertFalse(cmd.match("DROP sword").isPresent());
    }

    // ── GET FROM ─────────────────────────────────────────────────────────

    @Test
    void getFromMatchesFromSeparator() {
        GetFromCommand cmd = new GetFromCommand(new SocketCommandRegistry());
        CapturingContext context = new CapturingContext();

        cmd.match("GET sword from bag").orElseThrow().execute(context);

        assertEquals("sword", context.getFromItem);
        assertEquals("bag", context.getFromContainer);
    }

    @Test
    void getFromDoesNotMatchPlainGet() {
        GetFromCommand cmd = new GetFromCommand(new SocketCommandRegistry());
        assertFalse(cmd.match("GET sword").isPresent());
    }

    // ── GET routing ──────────────────────────────────────────────────────

    @Test
    void getMatchesPlainPickup() {
        GetCommand cmd = new GetCommand(new SocketCommandRegistry());
        CapturingContext context = new CapturingContext();

        cmd.match("GET sword").orElseThrow().execute(context);

        assertEquals("sword", context.gotItem);
    }

    @Test
    void getDoesNotMatchWhenFromPresent() {
        GetCommand cmd = new GetCommand(new SocketCommandRegistry());
        assertFalse(cmd.match("GET sword from bag").isPresent());
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private static final class CapturingContext implements SocketCommandContext {
        String promptMessage = "";
        String putItem;
        String putContainer;
        String getFromItem;
        String getFromContainer;
        String gotItem;

        @Override public boolean isAuthenticated() { return true; }
        @Override public Player getPlayer() { return null; }
        @Override public List<Client> clients() { return List.of(); }
        @Override public List<Username> onlinePlayerNames() { return List.of(); }
        @Override public void sendLook() {}
        @Override public void sendLookAt(String t) {}
        @Override public void sendMove(Direction d) {}
        @Override public void useAbility(String a) {}
        @Override public void updateAnsi(String a) {}
        @Override public void writeLineWithPrompt(String m) { promptMessage = m; }
        @Override public void writeLineSafe(String m) {}
        @Override public void sendPrompt() {}
        @Override public void sendToUsername(Username u, String m) {}
        @Override public void sendToRoom(Player s, Player t, String m) {}
        @Override public void sendToRoom(Player s, String m) {}
        @Override public Optional<Player> resolveTarget(Player s, String i) { return Optional.empty(); }
        @Override public void executeAttack(String a) {}
        @Override public void getItem(String a) { this.gotItem = a; }
        @Override public void dropItem(String a) {}
        @Override public void putIntoContainer(String item, String container) {
            this.putItem = item;
            this.putContainer = container;
        }
        @Override public void getFromContainer(String item, String container) {
            this.getFromItem = item;
            this.getFromContainer = container;
        }
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

package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.world.Direction;

/**
 * Unit tests for {@link CompareCommand}.
 */
class CompareCommandTest {

    private CompareCommand command;

    @BeforeEach
    void setUp() {
        command = new CompareCommand(new SocketCommandRegistry());
    }

    @Test
    void matchesCompareToken() {
        assertTrue(command.match("COMPARE sword").isPresent());
        assertTrue(command.match("compare sword").isPresent());
    }

    @Test
    void matchesCmpAlias() {
        assertTrue(command.match("CMP sword").isPresent());
        assertTrue(command.match("cmp sword").isPresent());
    }

    @Test
    void doesNotMatchOtherTokens() {
        assertFalse(command.match("examine sword").isPresent());
        assertFalse(command.match("look sword").isPresent());
        assertFalse(command.match("").isPresent());
    }

    @Test
    void passesArgsToContext() {
        AtomicReference<String> captured = new AtomicReference<>();
        CapturingContext context = new CapturingContext(captured);

        Optional<SocketCommandMatch> match = command.match("COMPARE iron-sword");
        assertTrue(match.isPresent());
        match.get().execute(context);

        assertEquals("iron-sword", captured.get());
    }

    @Test
    void passesArgsViaCmpAlias() {
        AtomicReference<String> captured = new AtomicReference<>();
        CapturingContext context = new CapturingContext(captured);

        Optional<SocketCommandMatch> match = command.match("CMP iron-sword");
        assertTrue(match.isPresent());
        match.get().execute(context);

        assertEquals("iron-sword", captured.get());
    }

    @Test
    void passesBlankArgsWhenNoTarget() {
        AtomicReference<String> captured = new AtomicReference<>();
        CapturingContext context = new CapturingContext(captured);

        Optional<SocketCommandMatch> match = command.match("COMPARE");
        assertTrue(match.isPresent());
        match.get().execute(context);

        assertEquals("", captured.get());
    }

    @Test
    void hasShortDescription() {
        assertNotNull(command.shortDescription());
        assertFalse(command.shortDescription().isBlank());
    }

    @Test
    void longDescriptionMentionsAliasAndUsage() {
        String desc = command.longDescription();
        assertNotNull(desc);
        assertTrue(desc.contains("COMPARE"));
        assertTrue(desc.contains("CMP"));
    }

    @Test
    void nameIsCompare() {
        assertEquals("compare", command.name());
    }

    // --- helpers ---

    private static class CapturingContext implements SocketCommandContext {
        private final AtomicReference<String> captured;

        CapturingContext(AtomicReference<String> captured) {
            this.captured = captured;
        }

        @Override public void compareItem(String args) { captured.set(args); }
        @Override public boolean isAuthenticated() { return true; }
        @Override public Player getPlayer() { return null; }
        @Override public List<Client> clients() { return List.of(); }
        @Override public List<Username> onlinePlayerNames() { return List.of(); }
        @Override public void sendLook() {}
        @Override public void sendLookAt(String t) {}
        @Override public void sendMove(Direction d) {}
        @Override public void useAbility(String a) {}
        @Override public void updateAnsi(String a) {}
        @Override public void writeLineWithPrompt(String m) {}
        @Override public void writeLineSafe(String m) {}
        @Override public void sendPrompt() {}
        @Override public void sendToUsername(Username u, String m) {}
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

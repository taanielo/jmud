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
 * Unit tests for {@link CutCommand}.
 */
class CutCommandTest {

    private CutCommand command;

    @BeforeEach
    void setUp() {
        command = new CutCommand(new SocketCommandRegistry());
    }

    @Test
    void matchesCutToken() {
        assertTrue(command.match("CUT quartz band").isPresent());
        assertTrue(command.match("cut").isPresent());
    }

    @Test
    void doesNotMatchOtherTokens() {
        assertFalse(command.match("tan boiled leather vest").isPresent());
        assertFalse(command.match("").isPresent());
    }

    @Test
    void passesArgsToContext() {
        AtomicReference<String> captured = new AtomicReference<>();
        CapturingContext context = new CapturingContext(captured);

        Optional<SocketCommandMatch> match = command.match("CUT quartz band");
        assertTrue(match.isPresent());
        match.get().execute(context);

        assertEquals("quartz band", captured.get());
    }

    @Test
    void passesEmptyArgsForBareCut() {
        AtomicReference<String> captured = new AtomicReference<>();
        CapturingContext context = new CapturingContext(captured);

        Optional<SocketCommandMatch> match = command.match("CUT");
        assertTrue(match.isPresent());
        match.get().execute(context);

        assertEquals("", captured.get());
    }

    @Test
    void nameIsCut() {
        assertEquals("cut", command.name());
    }

    @Test
    void hasDescriptions() {
        assertNotNull(command.shortDescription());
        assertFalse(command.shortDescription().isBlank());
        assertTrue(command.longDescription().contains("CUT"));
    }

    // --- helpers ---

    private static class CapturingContext implements SocketCommandContext {
        private final AtomicReference<String> captured;

        CapturingContext(AtomicReference<String> captured) {
            this.captured = captured;
        }

        @Override public void cut(String args) { captured.set(args); }
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
        @Override public void equipItem(String a) {}
        @Override public void unequipItem(String a) {}
        @Override public void quaffItem(String a) {}
        @Override public void readItem(String a) {}
        @Override public void writeItem(String a) {}
        @Override public void killMob(String a) {}
        @Override public void fleeCombat() {}
        @Override public void sendInventory() {}
        @Override public void sendEquipment() {}
        @Override public void sendMessage(io.taanielo.jmud.core.messaging.Message m) {}
        @Override public void close() {}
        @Override public void run() {}
    }
}

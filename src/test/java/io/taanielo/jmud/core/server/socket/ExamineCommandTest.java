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
 * Unit tests for {@link ExamineCommand}.
 */
class ExamineCommandTest {

    private ExamineCommand command;

    @BeforeEach
    void setUp() {
        command = new ExamineCommand(new SocketCommandRegistry());
    }

    @Test
    void matchesExamineToken() {
        assertTrue(command.match("EXAMINE sword").isPresent());
        assertTrue(command.match("examine sword").isPresent());
    }

    @Test
    void matchesExAlias() {
        assertTrue(command.match("EX sword").isPresent());
        assertTrue(command.match("ex sword").isPresent());
    }

    @Test
    void matchesExamAlias() {
        assertTrue(command.match("EXAM sword").isPresent());
        assertTrue(command.match("exam sword").isPresent());
    }

    @Test
    void doesNotMatchOtherTokens() {
        assertFalse(command.match("look sword").isPresent());
        assertFalse(command.match("get sword").isPresent());
        assertFalse(command.match("").isPresent());
    }

    @Test
    void passesArgsToContext() {
        AtomicReference<String> captured = new AtomicReference<>();
        CapturingContext context = new CapturingContext(captured);

        Optional<SocketCommandMatch> match = command.match("EXAMINE iron-sword");
        assertTrue(match.isPresent());
        match.get().execute(context);

        assertEquals("iron-sword", captured.get());
    }

    @Test
    void passesArgsViaExAlias() {
        AtomicReference<String> captured = new AtomicReference<>();
        CapturingContext context = new CapturingContext(captured);

        Optional<SocketCommandMatch> match = command.match("EX iron-sword");
        assertTrue(match.isPresent());
        match.get().execute(context);

        assertEquals("iron-sword", captured.get());
    }

    @Test
    void passesArgsViaExamAlias() {
        AtomicReference<String> captured = new AtomicReference<>();
        CapturingContext context = new CapturingContext(captured);

        Optional<SocketCommandMatch> match = command.match("EXAM health-potion");
        assertTrue(match.isPresent());
        match.get().execute(context);

        assertEquals("health-potion", captured.get());
    }

    @Test
    void passesBlankArgsWhenNoTarget() {
        AtomicReference<String> captured = new AtomicReference<>();
        CapturingContext context = new CapturingContext(captured);

        Optional<SocketCommandMatch> match = command.match("EXAMINE");
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
    void longDescriptionMentionsAllAliases() {
        String desc = command.longDescription();
        assertNotNull(desc);
        assertTrue(desc.contains("EXAMINE"));
        assertTrue(desc.contains("EX"));
        assertTrue(desc.contains("EXAM"));
    }

    @Test
    void nameIsExamine() {
        assertEquals("examine", command.name());
    }

    // --- helpers ---

    private static class CapturingContext implements SocketCommandContext {
        private final AtomicReference<String> captured;

        CapturingContext(AtomicReference<String> captured) {
            this.captured = captured;
        }

        @Override public void examineItem(String args) { captured.set(args); }
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

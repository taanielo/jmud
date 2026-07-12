package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AssistCommand} parsing and dispatch.
 */
class AssistCommandTest {

    @Test
    void matchesAssistTokenCaseInsensitively() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        AssistCommand command = new AssistCommand(registry);

        assertTrue(command.match("ASSIST Riona").isPresent());
        assertTrue(command.match("assist riona").isPresent());
    }

    @Test
    void doesNotMatchOtherTokens() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        AssistCommand command = new AssistCommand(registry);

        assertFalse(command.match("KILL rat").isPresent());
        assertFalse(command.match("attack rat").isPresent());
        assertFalse(command.match("").isPresent());
    }

    @Test
    void dispatchesArgsToExecuteAssist() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        AssistCommand command = new AssistCommand(registry);
        RecordingContext context = new RecordingContext();

        command.match("ASSIST Riona").get().execute(context);

        assertEquals(List.of("Riona"), context.assistArgs);
    }

    private static final class RecordingContext implements SocketCommandContext {
        private final List<String> assistArgs = new ArrayList<>();

        @Override public void executeAssist(String args) { assistArgs.add(args); }

        // ── unused interface surface ───────────────────────────────────
        @Override public boolean isAuthenticated() { return true; }
        @Override public io.taanielo.jmud.core.player.Player getPlayer() { return null; }
        @Override public List<io.taanielo.jmud.core.server.Client> clients() { return List.of(); }
        @Override public List<io.taanielo.jmud.core.authentication.Username> onlinePlayerNames() { return List.of(); }
        @Override public void sendLook() {}
        @Override public void sendLookAt(String t) {}
        @Override public void sendMove(io.taanielo.jmud.core.world.Direction d) {}
        @Override public void useAbility(String a) {}
        @Override public void updateAnsi(String a) {}
        @Override public void writeLineWithPrompt(String m) {}
        @Override public void writeLineSafe(String m) {}
        @Override public void sendPrompt() {}
        @Override public void sendToUsername(io.taanielo.jmud.core.authentication.Username u, String m) {}
        @Override public void sendToRoom(io.taanielo.jmud.core.player.Player s, io.taanielo.jmud.core.player.Player t, String m) {}
        @Override public void sendToRoom(io.taanielo.jmud.core.player.Player s, String m) {}
        @Override public java.util.Optional<io.taanielo.jmud.core.player.Player> resolveTarget(
            io.taanielo.jmud.core.player.Player s, String i) { return java.util.Optional.empty(); }
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

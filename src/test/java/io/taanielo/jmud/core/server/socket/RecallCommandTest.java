package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.world.Direction;

/**
 * Unit tests for {@link RecallCommand}.
 *
 * <p>Guard logic (in-combat, cooldown) and the actual teleport live in
 * {@link io.taanielo.jmud.core.action.GameActionService#recall} and are covered by
 * {@code GameActionServiceTest}. These tests verify token matching, command metadata, and that a
 * successful match delegates to {@link SocketCommandContext#recall()}.
 */
class RecallCommandTest {

    @Test
    void matchesRecallToken() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        RecallCommand command = new RecallCommand(registry);

        assertTrue(command.match("RECALL").isPresent());
        assertTrue(command.match("recall").isPresent());
        assertTrue(command.match("Recall").isPresent());
    }

    @Test
    void doesNotMatchOtherTokens() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        RecallCommand command = new RecallCommand(registry);

        assertFalse(command.match("look").isPresent());
        assertFalse(command.match("flee").isPresent());
        assertFalse(command.match("").isPresent());
        assertFalse(command.match("recal").isPresent());
    }

    @Test
    void successfulMatchDelegatesToRecall() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        RecallCommand command = new RecallCommand(registry);

        TrackingContext context = new TrackingContext();
        command.match("RECALL").get().execute(context);

        assertTrue(context.recallCalled, "A matched RECALL command must delegate to context.recall()");
    }

    @Test
    void hasShortAndLongDescriptions() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        RecallCommand command = new RecallCommand(registry);

        assertFalse(command.shortDescription().isBlank(), "shortDescription must not be blank");
        assertFalse(command.longDescription().isBlank(), "longDescription must not be blank");
    }

    @Test
    void nameIsCorrect() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        RecallCommand command = new RecallCommand(registry);

        assertTrue(command.name().equalsIgnoreCase("recall"),
                "Command name should be 'recall'");
    }

    @Test
    void registersItselfWithRegistry() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        new RecallCommand(registry);

        assertTrue(registry.commands().stream().anyMatch(c -> c instanceof RecallCommand),
                "RecallCommand should register itself with the registry");
    }

    // --- helpers ---

    private static class TrackingContext implements SocketCommandContext {
        boolean recallCalled = false;

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
        @Override public void recall() { recallCalled = true; }
        @Override public void sendInventory() {}
        @Override public void sendEquipment() {}
        @Override public void sendMessage(io.taanielo.jmud.core.messaging.Message m) {}
        @Override public void close() {}
        @Override public void run() {}
    }
}

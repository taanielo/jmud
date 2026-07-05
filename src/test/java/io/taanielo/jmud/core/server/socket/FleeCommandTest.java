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
 * Unit tests for {@link FleeCommand}.
 *
 * <p>Guard logic (not-in-combat, no-exits) lives in
 * {@link SocketCommandContextImpl#fleeCombat()} and is exercised via
 * integration paths. These tests verify token matching, command metadata, and
 * that a successful match delegates to {@link SocketCommandContext#fleeCombat()}.
 */
class FleeCommandTest {

    @Test
    void matchesFleeToken() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        FleeCommand command = new FleeCommand(registry);

        assertTrue(command.match("FLEE").isPresent());
        assertTrue(command.match("flee").isPresent());
        assertTrue(command.match("Flee").isPresent());
    }

    @Test
    void matchesFlAlias() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        FleeCommand command = new FleeCommand(registry);

        assertTrue(command.match("FL").isPresent());
        assertTrue(command.match("fl").isPresent());
    }

    @Test
    void doesNotMatchOtherTokens() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        FleeCommand command = new FleeCommand(registry);

        assertFalse(command.match("look").isPresent());
        assertFalse(command.match("kill").isPresent());
        assertFalse(command.match("").isPresent());
        assertFalse(command.match("fly").isPresent());
        assertFalse(command.match("f").isPresent());
    }

    @Test
    void successfulMatchDelegatesToFleeCombat() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        FleeCommand command = new FleeCommand(registry);

        TrackingContext context = new TrackingContext();
        command.match("FLEE").get().execute(context);

        assertTrue(context.fleeCalled, "A matched FLEE command must delegate to context.fleeCombat()");
    }

    @Test
    void flAliasAlsoDelegatesToFleeCombat() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        FleeCommand command = new FleeCommand(registry);

        TrackingContext context = new TrackingContext();
        command.match("FL").get().execute(context);

        assertTrue(context.fleeCalled, "A matched FL alias must delegate to context.fleeCombat()");
    }

    @Test
    void hasShortAndLongDescriptions() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        FleeCommand command = new FleeCommand(registry);

        assertFalse(command.shortDescription().isBlank(), "shortDescription must not be blank");
        assertFalse(command.longDescription().isBlank(), "longDescription must not be blank");
    }

    @Test
    void nameIsCorrect() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        FleeCommand command = new FleeCommand(registry);

        assertTrue(command.name().equalsIgnoreCase("flee"),
                "Command name should be 'flee'");
    }

    @Test
    void registersItselfWithRegistry() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        new FleeCommand(registry);

        assertTrue(registry.commands().stream().anyMatch(c -> c instanceof FleeCommand),
                "FleeCommand should register itself with the registry");
    }

    // --- helpers ---

    private static class TrackingContext implements SocketCommandContext {
        boolean fleeCalled = false;

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
        @Override public void equipItem(String a) {}
        @Override public void unequipItem(String a) {}
        @Override public void killMob(String a) {}
        @Override public void fleeCombat() { fleeCalled = true; }
        @Override public void sendInventory() {}
        @Override public void sendEquipment() {}
        @Override public void sendMessage(io.taanielo.jmud.core.messaging.Message m) {}
        @Override public void close() {}
        @Override public void run() {}
    }
}

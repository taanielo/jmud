package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * Unit tests for {@link BindCommand}.
 *
 * <p>Guard logic (in-combat, waypoint check) and the anchor mutation live in
 * {@link io.taanielo.jmud.core.action.GameActionService#bind} and are covered by
 * {@code GameActionServiceTest}. These tests verify token matching, argument capture, and delegation
 * to {@link SocketCommandContext#bind(String)}.
 */
class BindCommandTest {

    @Test
    void matchesBindToken() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        BindCommand command = new BindCommand(registry);

        assertTrue(command.match("BIND").isPresent());
        assertTrue(command.match("bind").isPresent());
        assertTrue(command.match("Bind here").isPresent());
    }

    @Test
    void doesNotMatchOtherTokens() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        BindCommand command = new BindCommand(registry);

        assertFalse(command.match("recall").isPresent());
        assertFalse(command.match("").isPresent());
        assertFalse(command.match("bin").isPresent());
    }

    @Test
    void bareBindDelegatesWithEmptyArgs() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        BindCommand command = new BindCommand(registry);

        TrackingContext context = new TrackingContext();
        command.match("BIND").get().execute(context);

        assertTrue(context.bindCalled);
        assertEquals("", context.bindArgs);
    }

    @Test
    void bindHereDelegatesWithArgs() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        BindCommand command = new BindCommand(registry);

        TrackingContext context = new TrackingContext();
        command.match("BIND HERE").get().execute(context);

        assertTrue(context.bindCalled);
        assertEquals("HERE", context.bindArgs);
    }

    @Test
    void hasShortAndLongDescriptions() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        BindCommand command = new BindCommand(registry);

        assertFalse(command.shortDescription().isBlank());
        assertFalse(command.longDescription().isBlank());
    }

    @Test
    void registersItselfWithRegistry() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        new BindCommand(registry);

        assertTrue(registry.commands().stream().anyMatch(c -> c instanceof BindCommand),
            "BindCommand should register itself with the registry");
    }

    // --- helpers ---

    private static class TrackingContext implements SocketCommandContext {
        boolean bindCalled = false;
        String bindArgs = null;

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
        @Override public void bind(String args) {
            bindCalled = true;
            bindArgs = args;
        }
        @Override public void sendInventory() {}
        @Override public void sendEquipment() {}
        @Override public void sendMessage(io.taanielo.jmud.core.messaging.Message m) {}
        @Override public void close() {}
        @Override public void run() {}
    }
}

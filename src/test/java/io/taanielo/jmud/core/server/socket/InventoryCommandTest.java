package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerVitals;
import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.world.Direction;

class InventoryCommandTest {

    @Test
    void matchesInventoryToken() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        InventoryCommand command = new InventoryCommand(registry);

        assertTrue(command.match("inventory").isPresent());
        assertTrue(command.match("INVENTORY").isPresent());
    }

    @Test
    void matchesInvAlias() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        InventoryCommand command = new InventoryCommand(registry);

        assertTrue(command.match("inv").isPresent());
        assertTrue(command.match("INV").isPresent());
    }

    @Test
    void matchesSingleLetterI() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        InventoryCommand command = new InventoryCommand(registry);

        assertTrue(command.match("i").isPresent());
        assertTrue(command.match("I").isPresent());
    }

    @Test
    void doesNotMatchOtherTokens() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        InventoryCommand command = new InventoryCommand(registry);

        assertFalse(command.match("look").isPresent());
        assertFalse(command.match("score").isPresent());
        assertFalse(command.match("").isPresent());
    }

    @Test
    void executionDelegatesToSendInventory() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        InventoryCommand command = new InventoryCommand(registry);
        Player player = makeAlivePlayer("hero");
        CapturingContext context = new CapturingContext(player, true);

        Optional<SocketCommandMatch> match = command.match("inventory");
        assertTrue(match.isPresent());
        match.get().execute(context);

        assertTrue(context.inventoryCalled, "sendInventory should have been called");
    }

    @Test
    void unauthenticatedPlayerGetsErrorMessage() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        InventoryCommand command = new InventoryCommand(registry);
        CapturingContext context = new CapturingContext(null, false);

        Optional<SocketCommandMatch> match = command.match("i");
        assertTrue(match.isPresent());
        match.get().execute(context);

        assertFalse(context.inventoryCalled);
        assertNotNull(context.lastPromptMessage);
    }

    private static Player makeAlivePlayer(String name) {
        return new Player(
            User.of(Username.of(name), Password.hash("pw", 1000)),
            1, 0,
            new PlayerVitals(20, 20, 10, 10, 10, 10),
            List.of(), "prompt", false, List.of(), null, null
        );
    }

    private static class CapturingContext implements SocketCommandContext {
        private final Player player;
        private final boolean authenticated;
        boolean inventoryCalled;
        String lastPromptMessage;

        CapturingContext(Player player, boolean authenticated) {
            this.player = player;
            this.authenticated = authenticated;
        }

        @Override public boolean isAuthenticated() { return authenticated; }
        @Override public Player getPlayer() { return player; }
        @Override public List<Client> clients() { return List.of(); }
        @Override public List<Username> onlinePlayerNames() { return List.of(); }
        @Override public void sendLook() {}
        @Override public void sendLookAt(String t) {}
        @Override public void sendMove(Direction d) {}
        @Override public void useAbility(String a) {}
        @Override public void updateAnsi(String a) {}
        @Override public void writeLineWithPrompt(String m) { lastPromptMessage = m; }
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
        @Override public void fleeCombat() {}
        @Override public void sendInventory() { inventoryCalled = true; }
        @Override public void sendEquipment() {}
        @Override public void sendMessage(io.taanielo.jmud.core.messaging.Message m) {}
        @Override public void close() {}
        @Override public void run() {}
    }
}

package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

class EquipmentCommandTest {

    @Test
    void matchesEquipmentToken() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        EquipmentCommand command = new EquipmentCommand(registry);

        assertTrue(command.match("equipment").isPresent());
        assertTrue(command.match("EQUIPMENT").isPresent());
    }

    @Test
    void matchesEqAlias() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        EquipmentCommand command = new EquipmentCommand(registry);

        assertTrue(command.match("eq").isPresent());
        assertTrue(command.match("EQ").isPresent());
    }

    @Test
    void doesNotMatchOtherTokens() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        EquipmentCommand command = new EquipmentCommand(registry);

        assertFalse(command.match("equip").isPresent());
        assertFalse(command.match("inventory").isPresent());
        assertFalse(command.match("").isPresent());
    }

    @Test
    void executionDelegatesToSendEquipment() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        EquipmentCommand command = new EquipmentCommand(registry);
        Player player = makeAlivePlayer("hero");
        CapturingContext context = new CapturingContext(player, true);

        Optional<SocketCommandMatch> match = command.match("eq");
        assertTrue(match.isPresent());
        match.get().execute(context);

        assertTrue(context.equipmentCalled, "sendEquipment should have been called");
    }

    @Test
    void unauthenticatedPlayerGetsErrorMessage() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        EquipmentCommand command = new EquipmentCommand(registry);
        CapturingContext context = new CapturingContext(null, false);

        Optional<SocketCommandMatch> match = command.match("equipment");
        assertTrue(match.isPresent());
        match.get().execute(context);

        assertFalse(context.equipmentCalled);
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
        boolean equipmentCalled;
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
        @Override public void readItem(String a) {}
        @Override public void writeItem(String a) {}
        @Override public void equipItem(String a) {}
        @Override public void unequipItem(String a) {}
        @Override public void killMob(String a) {}
        @Override public void fleeCombat() {}
        @Override public void sendInventory() {}
        @Override public void sendEquipment() { equipmentCalled = true; }
        @Override public void sendMessage(io.taanielo.jmud.core.messaging.Message m) {}
        @Override public void close() {}
        @Override public void run() {}
    }
}

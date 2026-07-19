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
 * Unit tests for {@link WayfindCommand}: token matching, argument capture, and delegation to
 * {@link SocketCommandContext#wayfind(String)}. The routing/messaging logic lives in
 * {@code WayfindService} and is covered by {@code WayfindServiceTest}.
 */
class WayfindCommandTest {

    @Test
    void matchesWayfindToken() {
        WayfindCommand command = new WayfindCommand(new SocketCommandRegistry());

        assertTrue(command.match("WAYFIND").isPresent());
        assertTrue(command.match("wayfind frozen peaks").isPresent());
    }

    @Test
    void doesNotMatchOtherTokens() {
        WayfindCommand command = new WayfindCommand(new SocketCommandRegistry());

        assertFalse(command.match("wayfin").isPresent());
        assertFalse(command.match("recall").isPresent());
        assertFalse(command.match("").isPresent());
    }

    @Test
    void bareWayfindDelegatesWithEmptyArgs() {
        WayfindCommand command = new WayfindCommand(new SocketCommandRegistry());
        TrackingContext context = new TrackingContext();

        command.match("WAYFIND").get().execute(context);

        assertTrue(context.wayfindCalled);
        assertEquals("", context.wayfindArgs);
    }

    @Test
    void wayfindWithAreaDelegatesWithArgs() {
        WayfindCommand command = new WayfindCommand(new SocketCommandRegistry());
        TrackingContext context = new TrackingContext();

        command.match("WAYFIND frozen peaks").get().execute(context);

        assertTrue(context.wayfindCalled);
        assertEquals("frozen peaks", context.wayfindArgs);
    }

    @Test
    void hasShortAndLongDescriptions() {
        WayfindCommand command = new WayfindCommand(new SocketCommandRegistry());

        assertFalse(command.shortDescription().isBlank());
        assertFalse(command.longDescription().isBlank());
    }

    @Test
    void registersItselfWithRegistry() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        new WayfindCommand(registry);

        assertTrue(registry.commands().stream().anyMatch(c -> c instanceof WayfindCommand),
            "WayfindCommand should register itself with the registry");
    }

    private static class TrackingContext implements SocketCommandContext {
        boolean wayfindCalled = false;
        String wayfindArgs = null;

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
        @Override public void wayfind(String args) {
            wayfindCalled = true;
            wayfindArgs = args;
        }
        @Override public void sendInventory() {}
        @Override public void sendEquipment() {}
        @Override public void sendMessage(io.taanielo.jmud.core.messaging.Message m) {}
        @Override public void close() {}
        @Override public void run() {}
    }
}

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
 * Unit tests for {@link SneakCommand}: verifies that {@code SNEAK} and its {@code HIDE} alias route
 * to {@link SocketCommandContext#sneak(String)} while other tokens are ignored.
 */
class SneakCommandTest {

    @Test
    void sneakTokenRoutesToSneak() {
        SneakCommand cmd = new SneakCommand(new SocketCommandRegistry());
        CapturingContext context = new CapturingContext();

        cmd.match("SNEAK").orElseThrow().execute(context);

        assertTrue(context.sneaked);
    }

    @Test
    void hideAliasRoutesToSneak() {
        SneakCommand cmd = new SneakCommand(new SocketCommandRegistry());
        CapturingContext context = new CapturingContext();

        cmd.match("HIDE").orElseThrow().execute(context);

        assertTrue(context.sneaked);
    }

    @Test
    void doesNotMatchOtherTokens() {
        SneakCommand cmd = new SneakCommand(new SocketCommandRegistry());

        assertFalse(cmd.match("PICK chest").isPresent());
        assertFalse(cmd.match("SNEAKY").isPresent());
    }

    @Test
    void nameIsSneak() {
        SneakCommand cmd = new SneakCommand(new SocketCommandRegistry());

        assertEquals("sneak", cmd.name());
    }

    private static final class CapturingContext implements SocketCommandContext {
        boolean sneaked;

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
        @Override public void sneak(String a) { this.sneaked = true; }
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

package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.world.Direction;

/**
 * Unit tests for {@link StealCommand}: verifies the {@code STEAL <npc>} parsing routes the target
 * argument to {@link SocketCommandContext#steal(String)} and shows usage when empty.
 */
class StealCommandTest {

    @Test
    void stealRoutesNpcArgument() {
        StealCommand cmd = new StealCommand(new SocketCommandRegistry());
        CapturingContext context = new CapturingContext();

        cmd.match("STEAL bandit captain").orElseThrow().execute(context);

        assertEquals("bandit captain", context.stealTarget);
    }

    @Test
    void stealWithoutArgumentShowsUsage() {
        StealCommand cmd = new StealCommand(new SocketCommandRegistry());
        CapturingContext context = new CapturingContext();

        cmd.match("STEAL").orElseThrow().execute(context);

        assertTrue(context.promptMessage.contains("Usage"));
        assertNull(context.stealTarget);
    }

    @Test
    void stealDoesNotMatchOtherTokens() {
        StealCommand cmd = new StealCommand(new SocketCommandRegistry());

        assertFalse(cmd.match("SNEAK").isPresent());
        assertFalse(cmd.match("GET chest").isPresent());
    }

    private static final class CapturingContext implements SocketCommandContext {
        String promptMessage = "";
        String stealTarget;

        @Override public boolean isAuthenticated() { return true; }
        @Override public Player getPlayer() { return null; }
        @Override public List<Client> clients() { return List.of(); }
        @Override public List<Username> onlinePlayerNames() { return List.of(); }
        @Override public void sendLook() {}
        @Override public void sendLookAt(String t) {}
        @Override public void sendMove(Direction d) {}
        @Override public void useAbility(String a) {}
        @Override public void updateAnsi(String a) {}
        @Override public void writeLineWithPrompt(String m) { promptMessage = m; }
        @Override public void writeLineSafe(String m) {}
        @Override public void sendPrompt() {}
        @Override public void sendToUsername(Username u, String m) {}
        @Override public void sendToRoom(Player s, Player t, String m) {}
        @Override public void sendToRoom(Player s, String m) {}
        @Override public Optional<Player> resolveTarget(Player s, String i) { return Optional.empty(); }
        @Override public void executeAttack(String a) {}
        @Override public void getItem(String a) {}
        @Override public void dropItem(String a) {}
        @Override public void steal(String a) { this.stealTarget = a; }
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

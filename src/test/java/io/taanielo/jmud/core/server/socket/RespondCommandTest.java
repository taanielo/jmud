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
 * Unit tests for {@link RespondCommand}: verifies {@code RESPOND <number>} routes the argument to
 * {@link SocketCommandContext#respond(String)} and shows usage when empty.
 */
class RespondCommandTest {

    @Test
    void respondRoutesNumberArgument() {
        RespondCommand cmd = new RespondCommand(new SocketCommandRegistry());
        CapturingContext context = new CapturingContext();

        cmd.match("RESPOND 2").orElseThrow().execute(context);

        assertEquals("2", context.respondArg);
    }

    @Test
    void respondWithoutArgumentShowsUsage() {
        RespondCommand cmd = new RespondCommand(new SocketCommandRegistry());
        CapturingContext context = new CapturingContext();

        cmd.match("RESPOND").orElseThrow().execute(context);

        assertTrue(context.promptMessage.contains("Usage"));
        assertNull(context.respondArg);
    }

    @Test
    void respondDoesNotMatchOtherTokens() {
        RespondCommand cmd = new RespondCommand(new SocketCommandRegistry());

        assertFalse(cmd.match("TALK borin").isPresent());
        assertFalse(cmd.match("REST").isPresent());
    }

    private static final class CapturingContext implements SocketCommandContext {
        String promptMessage = "";
        String respondArg;

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
        @Override public void respond(String a) { this.respondArg = a; }
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

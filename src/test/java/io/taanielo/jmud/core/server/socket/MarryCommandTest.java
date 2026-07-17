package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.world.Direction;

/**
 * Unit tests for {@link MarryCommand} parsing and dispatch.
 */
class MarryCommandTest {

    @Test
    void matchesMarryToken() {
        MarryCommand cmd = new MarryCommand(new SocketCommandRegistry());
        assertTrue(cmd.match("MARRY Bob").isPresent());
        assertTrue(cmd.match("marry").isPresent());
    }

    @Test
    void doesNotMatchOtherTokens() {
        MarryCommand cmd = new MarryCommand(new SocketCommandRegistry());
        assertFalse(cmd.match("MARRIAGE Bob").isPresent());
        assertFalse(cmd.match("").isPresent());
    }

    @Test
    void passesProposalTargetThrough() {
        MarryCommand cmd = new MarryCommand(new SocketCommandRegistry());
        CapturingContext context = new CapturingContext();

        cmd.match("MARRY Bob").orElseThrow().execute(context);

        assertEquals("Bob", context.marryArgs);
    }

    @Test
    void passesSubCommandThrough() {
        MarryCommand cmd = new MarryCommand(new SocketCommandRegistry());
        CapturingContext context = new CapturingContext();

        cmd.match("MARRY ACCEPT").orElseThrow().execute(context);

        assertEquals("ACCEPT", context.marryArgs);
    }

    @Test
    void passesBlankArgumentsForStatus() {
        MarryCommand cmd = new MarryCommand(new SocketCommandRegistry());
        CapturingContext context = new CapturingContext();

        cmd.match("MARRY").orElseThrow().execute(context);

        assertEquals("", context.marryArgs);
    }

    private static final class CapturingContext implements SocketCommandContext {
        String marryArgs;
        private final Player player = Player.of(
            User.of(Username.of("Alice"), Password.hash("secret")), "%h/%H hp>");

        @Override public void executeMarry(String args) { this.marryArgs = args; }

        @Override public boolean isAuthenticated() { return true; }
        @Override public Player getPlayer() { return player; }
        @Override public List<Client> clients() { return List.of(); }
        @Override public List<Username> onlinePlayerNames() { return new ArrayList<>(); }
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
        @Override public void sendInventory() {}
        @Override public void sendEquipment() {}
        @Override public void sendMessage(io.taanielo.jmud.core.messaging.Message m) {}
        @Override public void close() {}
        @Override public void run() {}
    }
}

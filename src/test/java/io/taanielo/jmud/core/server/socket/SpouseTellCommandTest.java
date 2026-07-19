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
 * Unit tests for {@link SpouseTellCommand} parsing and dispatch.
 */
class SpouseTellCommandTest {

    @Test
    void matchesSpouseTellAndAlias() {
        SpouseTellCommand cmd = new SpouseTellCommand(new SocketCommandRegistry());
        assertTrue(cmd.match("SPOUSETELL hello").isPresent());
        assertTrue(cmd.match("ST hey there").isPresent());
    }

    @Test
    void doesNotMatchOtherTokens() {
        SpouseTellCommand cmd = new SpouseTellCommand(new SocketCommandRegistry());
        assertFalse(cmd.match("TELL Bob hi").isPresent());
        assertFalse(cmd.match("").isPresent());
    }

    @Test
    void passesMessageThrough() {
        SpouseTellCommand cmd = new SpouseTellCommand(new SocketCommandRegistry());
        CapturingContext context = new CapturingContext();

        cmd.match("SPOUSETELL good morning love").orElseThrow().execute(context);

        assertEquals("good morning love", context.spouseTellArgs);
    }

    @Test
    void passesMessageThroughAlias() {
        SpouseTellCommand cmd = new SpouseTellCommand(new SocketCommandRegistry());
        CapturingContext context = new CapturingContext();

        cmd.match("ST see you soon").orElseThrow().execute(context);

        assertEquals("see you soon", context.spouseTellArgs);
    }

    private static final class CapturingContext implements SocketCommandContext {
        String spouseTellArgs;
        private final Player player = Player.of(
            User.of(Username.of("Alice"), Password.hash("secret")), "%h/%H hp>");

        @Override public void spouseTell(String message) { this.spouseTellArgs = message; }

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

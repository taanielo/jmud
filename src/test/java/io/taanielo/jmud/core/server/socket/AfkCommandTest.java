package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.world.Direction;

/**
 * Unit tests for {@link AfkCommand} token matching and argument forwarding.
 */
class AfkCommandTest {

    private final AfkCommand command = new AfkCommand(new SocketCommandRegistry());

    @Test
    void matchesAfkToken() {
        assertTrue(command.match("AFK").isPresent());
        assertTrue(command.match("afk").isPresent());
        assertTrue(command.match("AFK grabbing coffee").isPresent());
    }

    @Test
    void doesNotMatchOtherTokens() {
        assertFalse(command.match("WHO").isPresent());
        assertFalse(command.match("").isPresent());
    }

    @Test
    void nameIsAfk() {
        assertEquals("afk", command.name());
    }

    @Test
    void hasShortDescription() {
        assertNotNull(command.shortDescription());
        assertFalse(command.shortDescription().isBlank());
    }

    @Test
    void forwardsBlankArgsForBareToken() {
        AtomicReference<String> captured = new AtomicReference<>();
        SocketCommandContext ctx = new StubContext() {
            @Override
            public void toggleAfk(String args) {
                captured.set(args);
            }
        };
        command.match("AFK").orElseThrow().execute(ctx);
        assertEquals("", captured.get());
    }

    @Test
    void forwardsCustomMessageToContext() {
        AtomicReference<String> captured = new AtomicReference<>();
        SocketCommandContext ctx = new StubContext() {
            @Override
            public void toggleAfk(String args) {
                captured.set(args);
            }
        };
        command.match("AFK grabbing coffee").orElseThrow().execute(ctx);
        assertEquals("grabbing coffee", captured.get());
    }

    private static class StubContext implements SocketCommandContext {
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
        @Override public void sendInventory() {}
        @Override public void sendEquipment() {}
        @Override public void sendMessage(io.taanielo.jmud.core.messaging.Message m) {}
        @Override public void close() {}
        @Override public void run() {}
    }
}

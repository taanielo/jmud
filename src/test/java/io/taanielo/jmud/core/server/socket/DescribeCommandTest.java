package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.world.Direction;

/**
 * Unit tests for {@link DescribeCommand} token matching and argument forwarding.
 */
class DescribeCommandTest {

    private final DescribeCommand command = new DescribeCommand(new SocketCommandRegistry());

    @Test
    void matchesDescribeAndDescTokens() {
        assertTrue(command.match("DESCRIBE").isPresent());
        assertTrue(command.match("describe A grizzled dwarf.").isPresent());
        assertTrue(command.match("DESC").isPresent());
        assertTrue(command.match("desc CLEAR").isPresent());
    }

    @Test
    void doesNotMatchOtherTokens() {
        assertFalse(command.match("WHO").isPresent());
        assertFalse(command.match("DESCRIPTION").isPresent());
        assertFalse(command.match("").isPresent());
    }

    @Test
    void forwardsArgsToContext() {
        AtomicReference<String> captured = new AtomicReference<>();
        SocketCommandContext ctx = new StubContext() {
            @Override
            public void manageDescription(String args) {
                captured.set(args);
            }
        };
        command.match("DESCRIBE A grizzled dwarf with a notched axe.").orElseThrow().execute(ctx);
        assertEquals("A grizzled dwarf with a notched axe.", captured.get());
    }

    @Test
    void forwardsBlankArgsForBareDescribeToken() {
        AtomicReference<String> captured = new AtomicReference<>();
        SocketCommandContext ctx = new StubContext() {
            @Override
            public void manageDescription(String args) {
                captured.set(args);
            }
        };
        command.match("DESC").orElseThrow().execute(ctx);
        assertEquals("", captured.get());
    }

    @Test
    void nameIsDescribe() {
        assertEquals("describe", command.name());
    }

    @Test
    void hasDescriptions() {
        assertNotNull(command.shortDescription());
        assertFalse(command.shortDescription().isBlank());
        assertTrue(command.longDescription().contains("DESCRIBE"));
    }

    private static class StubContext implements SocketCommandContext {
        @Override public boolean isAuthenticated() { return true; }
        @Override public Player getPlayer() { return null; }
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

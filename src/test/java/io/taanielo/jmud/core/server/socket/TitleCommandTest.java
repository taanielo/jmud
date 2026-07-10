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
 * Unit tests for {@link TitleCommand} token matching and argument forwarding.
 */
class TitleCommandTest {

    private final TitleCommand command = new TitleCommand(new SocketCommandRegistry());

    @Test
    void matchesTitleToken() {
        assertTrue(command.match("TITLE").isPresent());
        assertTrue(command.match("title Centurion").isPresent());
        assertTrue(command.match("Title NONE").isPresent());
    }

    @Test
    void doesNotMatchOtherTokens() {
        assertFalse(command.match("WHO").isPresent());
        assertFalse(command.match("").isPresent());
    }

    @Test
    void forwardsArgsToContext() {
        AtomicReference<String> captured = new AtomicReference<>();
        SocketCommandContext ctx = new StubContext() {
            @Override
            public void manageTitle(String args) {
                captured.set(args);
            }
        };
        command.match("TITLE Centurion").orElseThrow().execute(ctx);
        assertEquals("Centurion", captured.get());
    }

    @Test
    void forwardsBlankArgsForBareTitleToken() {
        AtomicReference<String> captured = new AtomicReference<>();
        SocketCommandContext ctx = new StubContext() {
            @Override
            public void manageTitle(String args) {
                captured.set(args);
            }
        };
        command.match("TITLE").orElseThrow().execute(ctx);
        assertEquals("", captured.get());
    }

    @Test
    void nameIsTitle() {
        assertEquals("title", command.name());
    }

    @Test
    void hasShortDescription() {
        assertNotNull(command.shortDescription());
        assertFalse(command.shortDescription().isBlank());
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

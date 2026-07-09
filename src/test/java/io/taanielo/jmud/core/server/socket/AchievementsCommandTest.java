package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.messaging.Message;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.world.Direction;

/**
 * Unit tests for {@link AchievementsCommand} token matching and dispatch.
 */
class AchievementsCommandTest {

    private AchievementsCommand command;

    @BeforeEach
    void setUp() {
        command = new AchievementsCommand(new SocketCommandRegistry());
    }

    @Test
    void matchesAchievementsToken() {
        assertTrue(command.match("ACHIEVEMENTS").isPresent());
        assertTrue(command.match("achievements").isPresent());
    }

    @Test
    void doesNotMatchOtherTokens() {
        assertFalse(command.match("QUEST").isPresent());
        assertFalse(command.match("").isPresent());
        assertFalse(command.match("ACH").isPresent());
    }

    @Test
    void dispatchesToShowAchievements() {
        AtomicBoolean called = new AtomicBoolean(false);
        SocketCommandContext ctx = new StubContext() {
            @Override
            public void showAchievements() {
                called.set(true);
            }
        };

        command.match("ACHIEVEMENTS").orElseThrow().execute(ctx);

        assertTrue(called.get(), "ACHIEVEMENTS should dispatch to showAchievements()");
    }

    @Test
    void nameIsAchievements() {
        assertEquals("achievements", command.name());
    }

    @Test
    void hasDescriptions() {
        assertNotNull(command.shortDescription());
        assertFalse(command.shortDescription().isBlank());
        assertTrue(command.longDescription().toUpperCase(Locale.ROOT).contains("ACHIEVEMENTS"));
    }

    @Test
    void registersItselfWithRegistry() {
        SocketCommandRegistry reg = new SocketCommandRegistry();
        new AchievementsCommand(reg);
        assertTrue(reg.commands().stream().anyMatch(c -> c instanceof AchievementsCommand));
    }

    /** Minimal no-op context implementing only the interface's non-default methods. */
    static class StubContext implements SocketCommandContext {
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
        @Override public void sendMessage(Message m) {}
        @Override public void close() {}
        @Override public void run() {}
    }
}

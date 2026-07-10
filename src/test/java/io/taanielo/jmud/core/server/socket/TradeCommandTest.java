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
 * Unit tests for {@link TradeCommand} parsing and dispatch.
 */
class TradeCommandTest {

    @Test
    void matchesTradeToken() {
        TradeCommand cmd = new TradeCommand(new SocketCommandRegistry());
        assertTrue(cmd.match("TRADE Bob").isPresent());
        assertTrue(cmd.match("trade").isPresent());
    }

    @Test
    void doesNotMatchOtherTokens() {
        TradeCommand cmd = new TradeCommand(new SocketCommandRegistry());
        assertFalse(cmd.match("GIVE Bob torch").isPresent());
        assertFalse(cmd.match("").isPresent());
    }

    @Test
    void passesArgumentsThroughToExecuteTrade() {
        TradeCommand cmd = new TradeCommand(new SocketCommandRegistry());
        CapturingContext context = new CapturingContext();

        cmd.match("TRADE ADD GOLD 100").get().execute(context);

        assertEquals("ADD GOLD 100", context.tradeArgs);
    }

    @Test
    void passesBlankArgumentsForStatus() {
        TradeCommand cmd = new TradeCommand(new SocketCommandRegistry());
        CapturingContext context = new CapturingContext();

        cmd.match("TRADE").get().execute(context);

        assertEquals("", context.tradeArgs);
    }

    private static final class CapturingContext implements SocketCommandContext {
        String tradeArgs;
        private final Player player = Player.of(
            User.of(Username.of("Alice"), Password.hash("secret")), "%h/%H hp>");

        @Override public void executeTrade(String args) { this.tradeArgs = args; }

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

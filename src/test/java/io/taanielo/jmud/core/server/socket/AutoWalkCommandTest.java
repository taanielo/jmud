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
 * Unit tests for {@link AutoWalkCommand}: verifies {@code AUTOWALK <area>} / {@code AUTOWALK STOP}
 * parsing routes the argument to {@link SocketCommandContext#autoWalk(String)} and that it does not
 * match unrelated tokens.
 */
class AutoWalkCommandTest {

    @Test
    void routesAreaArgument() {
        AutoWalkCommand cmd = new AutoWalkCommand(new SocketCommandRegistry());
        CapturingContext context = new CapturingContext();

        cmd.match("AUTOWALK frozen peaks").orElseThrow().execute(context);

        assertEquals("frozen peaks", context.autoWalkArgs);
    }

    @Test
    void routesStopArgument() {
        AutoWalkCommand cmd = new AutoWalkCommand(new SocketCommandRegistry());
        CapturingContext context = new CapturingContext();

        cmd.match("AUTOWALK stop").orElseThrow().execute(context);

        assertEquals("stop", context.autoWalkArgs);
    }

    @Test
    void bareCommandRoutesEmptyArgument() {
        AutoWalkCommand cmd = new AutoWalkCommand(new SocketCommandRegistry());
        CapturingContext context = new CapturingContext();

        cmd.match("AUTOWALK").orElseThrow().execute(context);

        assertEquals("", context.autoWalkArgs);
    }

    @Test
    void doesNotMatchOtherTokens() {
        AutoWalkCommand cmd = new AutoWalkCommand(new SocketCommandRegistry());

        assertFalse(cmd.match("WAYFIND peaks").isPresent());
        assertFalse(cmd.match("AUTO on").isPresent());
    }

    @Test
    void reportsNameAndHelp() {
        AutoWalkCommand cmd = new AutoWalkCommand(new SocketCommandRegistry());

        assertEquals("autowalk", cmd.name());
        assertTrue(cmd.longDescription().contains("AUTOWALK"));
        assertTrue(cmd.longDescription().contains("WAYFIND"));
        assertTrue(cmd.longDescription().toLowerCase(java.util.Locale.ROOT).contains("ferry"));
        assertTrue(cmd.longDescription().contains("STOP"));
    }

    private static final class CapturingContext implements SocketCommandContext {
        String autoWalkArgs;

        @Override public boolean isAuthenticated() { return true; }
        @Override public Player getPlayer() { return null; }
        @Override public List<Client> clients() { return List.of(); }
        @Override public List<Username> onlinePlayerNames() { return List.of(); }
        @Override public void sendLook() {}
        @Override public void sendLookAt(String t) {}
        @Override public void sendMove(Direction d) {}
        @Override public void useAbility(String a) {}
        @Override public void updateAnsi(String a) {}
        @Override public void autoWalk(String a) { this.autoWalkArgs = a; }
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
        @Override public void track(String a) {}
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

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
 * Unit tests for {@link BriefCommand}: verifies {@code BRIEF [on|off|toggle|status]} parsing routes
 * the sub-command argument to {@link SocketCommandContext#updateBrief(String)} and that it does not
 * match unrelated tokens.
 */
class BriefCommandTest {

    @Test
    void routesOnArgument() {
        BriefCommand cmd = new BriefCommand(new SocketCommandRegistry());
        CapturingContext context = new CapturingContext();

        cmd.match("BRIEF on").orElseThrow().execute(context);

        assertEquals("on", context.briefArgs);
    }

    @Test
    void routesToggleArgument() {
        BriefCommand cmd = new BriefCommand(new SocketCommandRegistry());
        CapturingContext context = new CapturingContext();

        cmd.match("BRIEF toggle").orElseThrow().execute(context);

        assertEquals("toggle", context.briefArgs);
    }

    @Test
    void bareCommandRoutesEmptyArgument() {
        BriefCommand cmd = new BriefCommand(new SocketCommandRegistry());
        CapturingContext context = new CapturingContext();

        cmd.match("BRIEF").orElseThrow().execute(context);

        assertEquals("", context.briefArgs);
    }

    @Test
    void doesNotMatchOtherTokens() {
        BriefCommand cmd = new BriefCommand(new SocketCommandRegistry());

        assertFalse(cmd.match("ANSI on").isPresent());
        assertFalse(cmd.match("GET chest").isPresent());
    }

    @Test
    void reportsNameAndHelp() {
        BriefCommand cmd = new BriefCommand(new SocketCommandRegistry());

        assertEquals("brief", cmd.name());
        assertTrue(cmd.longDescription().contains("BRIEF"));
        assertTrue(cmd.longDescription().contains("LOOK"),
            "HELP entry should mention that LOOK always shows the full description");
    }

    private static final class CapturingContext implements SocketCommandContext {
        String briefArgs;

        @Override public boolean isAuthenticated() { return true; }
        @Override public Player getPlayer() { return null; }
        @Override public List<Client> clients() { return List.of(); }
        @Override public List<Username> onlinePlayerNames() { return List.of(); }
        @Override public void sendLook() {}
        @Override public void sendLookAt(String t) {}
        @Override public void sendMove(Direction d) {}
        @Override public void useAbility(String a) {}
        @Override public void updateAnsi(String a) {}
        @Override public void updateBrief(String a) { this.briefArgs = a; }
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

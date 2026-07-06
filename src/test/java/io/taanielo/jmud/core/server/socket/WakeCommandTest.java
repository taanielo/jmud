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
 * Unit tests for {@link WakeCommand}.
 */
class WakeCommandTest {

    // ── token matching ─────────────────────────────────────────────────

    @Test
    void matchesWakeToken() {
        WakeCommand cmd = new WakeCommand(new SocketCommandRegistry());
        assertTrue(cmd.match("WAKE").isPresent());
        assertTrue(cmd.match("wake").isPresent());
    }

    @Test
    void matchesStandAlias() {
        WakeCommand cmd = new WakeCommand(new SocketCommandRegistry());
        assertTrue(cmd.match("STAND").isPresent());
        assertTrue(cmd.match("stand").isPresent());
    }

    @Test
    void doesNotMatchOtherTokens() {
        WakeCommand cmd = new WakeCommand(new SocketCommandRegistry());
        assertFalse(cmd.match("REST").isPresent());
        assertFalse(cmd.match("SLEEP").isPresent());
        assertFalse(cmd.match("LOOK").isPresent());
        assertFalse(cmd.match("").isPresent());
    }

    // ── delegation ─────────────────────────────────────────────────────

    @Test
    void executionDelegatesToStopResting() {
        CapturingContext ctx = new CapturingContext("Alice");
        WakeCommand cmd = new WakeCommand(new SocketCommandRegistry());

        cmd.match("WAKE").get().execute(ctx);

        assertTrue(ctx.stopRestingCalled,
            "WAKE command must delegate to context.stopResting()");
    }

    @Test
    void standAliasAlsoDelegatesToStopResting() {
        CapturingContext ctx = new CapturingContext("Alice");
        WakeCommand cmd = new WakeCommand(new SocketCommandRegistry());

        cmd.match("STAND").get().execute(ctx);

        assertTrue(ctx.stopRestingCalled,
            "STAND alias must delegate to context.stopResting()");
    }

    @Test
    void stopRestingMessageIsStandUp() {
        CapturingContext ctx = new CapturingContext("Alice");
        WakeCommand cmd = new WakeCommand(new SocketCommandRegistry());

        cmd.match("WAKE").get().execute(ctx);

        assertEquals("You stand up.", ctx.stopRestingMessage,
            "WAKE command should pass 'You stand up.' as the wake message");
    }

    // ── metadata ───────────────────────────────────────────────────────

    @Test
    void shortDescriptionMentionsStandAlias() {
        WakeCommand cmd = new WakeCommand(new SocketCommandRegistry());
        assertTrue(cmd.shortDescription().contains("STAND"),
            "shortDescription should mention STAND alias");
    }

    @Test
    void longDescriptionContainsUsage() {
        WakeCommand cmd = new WakeCommand(new SocketCommandRegistry());
        String desc = cmd.longDescription();
        assertTrue(desc.contains("WAKE"), "longDescription should mention WAKE");
        assertTrue(desc.contains("Usage"), "longDescription should contain usage instructions");
    }

    // ── helpers ────────────────────────────────────────────────────────

    private static Player stubPlayer(String name) {
        User user = User.of(Username.of(name), Password.hash("secret"));
        return Player.of(user, "%h/%H hp>");
    }

    private static class CapturingContext implements SocketCommandContext {
        boolean stopRestingCalled = false;
        String stopRestingMessage = null;
        String promptMessage = "";
        final List<String> lines = new ArrayList<>();
        private final Player player;

        CapturingContext(String playerName) {
            this.player = stubPlayer(playerName);
        }

        @Override public void stopResting(String message) {
            stopRestingCalled = true;
            stopRestingMessage = message;
        }
        @Override public boolean isAuthenticated() { return true; }
        @Override public Player getPlayer() { return player; }
        @Override public List<Client> clients() { return List.of(); }
        @Override public List<Username> onlinePlayerNames() { return List.of(); }
        @Override public void sendLook() {}
        @Override public void sendLookAt(String t) {}
        @Override public void sendMove(Direction d) {}
        @Override public void useAbility(String a) {}
        @Override public void updateAnsi(String a) {}
        @Override public void writeLineWithPrompt(String m) { promptMessage = m; }
        @Override public void writeLineSafe(String m) { lines.add(m); }
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

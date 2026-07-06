package io.taanielo.jmud.core.server.socket;

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
 * Unit tests for {@link RestCommand}.
 */
class RestCommandTest {

    // ── token matching ─────────────────────────────────────────────────

    @Test
    void matchesRestToken() {
        RestCommand cmd = new RestCommand(new SocketCommandRegistry());
        assertTrue(cmd.match("REST").isPresent());
        assertTrue(cmd.match("rest").isPresent());
    }

    @Test
    void matchesSleepAlias() {
        RestCommand cmd = new RestCommand(new SocketCommandRegistry());
        assertTrue(cmd.match("SLEEP").isPresent());
        assertTrue(cmd.match("sleep").isPresent());
    }

    @Test
    void doesNotMatchOtherTokens() {
        RestCommand cmd = new RestCommand(new SocketCommandRegistry());
        assertFalse(cmd.match("WAKE").isPresent());
        assertFalse(cmd.match("STAND").isPresent());
        assertFalse(cmd.match("LOOK").isPresent());
        assertFalse(cmd.match("").isPresent());
    }

    // ── delegation ─────────────────────────────────────────────────────

    @Test
    void executionDelegatesToStartResting() {
        CapturingContext ctx = new CapturingContext("Alice");
        RestCommand cmd = new RestCommand(new SocketCommandRegistry());

        cmd.match("REST").get().execute(ctx);

        assertTrue(ctx.startRestingCalled,
            "REST command must delegate to context.startResting()");
    }

    @Test
    void sleepAliasAlsoDelegatesToStartResting() {
        CapturingContext ctx = new CapturingContext("Alice");
        RestCommand cmd = new RestCommand(new SocketCommandRegistry());

        cmd.match("SLEEP").get().execute(ctx);

        assertTrue(ctx.startRestingCalled,
            "SLEEP alias must delegate to context.startResting()");
    }

    // ── metadata ───────────────────────────────────────────────────────

    @Test
    void shortDescriptionMentionsSleepAlias() {
        RestCommand cmd = new RestCommand(new SocketCommandRegistry());
        assertTrue(cmd.shortDescription().contains("SLEEP"),
            "shortDescription should mention SLEEP alias");
    }

    @Test
    void longDescriptionContainsUsage() {
        RestCommand cmd = new RestCommand(new SocketCommandRegistry());
        String desc = cmd.longDescription();
        assertTrue(desc.contains("REST"), "longDescription should mention REST");
        assertTrue(desc.contains("Usage"), "longDescription should contain usage instructions");
    }

    // ── helpers ────────────────────────────────────────────────────────

    private static Player stubPlayer(String name) {
        User user = User.of(Username.of(name), Password.hash("secret"));
        return Player.of(user, "%h/%H hp>");
    }

    private static class CapturingContext implements SocketCommandContext {
        boolean startRestingCalled = false;
        String promptMessage = "";
        final List<String> lines = new ArrayList<>();
        private final Player player;

        CapturingContext(String playerName) {
            this.player = stubPlayer(playerName);
        }

        @Override public void startResting() { startRestingCalled = true; }
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
